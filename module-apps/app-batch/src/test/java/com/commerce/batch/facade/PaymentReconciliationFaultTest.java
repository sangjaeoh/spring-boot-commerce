package com.commerce.batch.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.commerce.batch.BatchIntegrationTest;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.order.application.provided.OrderAppender;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.CancellationReason;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.order.domain.OrderStatus;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentAppender;
import com.commerce.payment.application.provided.PaymentProcessor;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.application.required.PaymentApproval;
import com.commerce.payment.application.required.PaymentGateway;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.payment.domain.PaymentStatus;
import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockModifier;
import com.commerce.stock.application.provided.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 리컨실 사각지대 잔여의 다음 스윕 수렴을 검증하는 테스트다.
 *
 * <p>(1) 결제 승인 커밋과 주문 결제완료({@code markPaid}) 사이 중단이 남긴 payment=APPROVED·order=PENDING
 * 잔여를 PENDING 스윕이 발견해 결제 리컨실이 결제완료로 완결한다. (2) 고아 청구 환불의 PG 환불 실패 — 승인
 * 기록이 커밋되지 않아 결제가 REQUESTED로 남고(리컨실 대상 이탈 없음) 다음 스윕이 환불을 완결한다(자기복구).
 * 잔여 상태는 도메인 서비스 직접 호출로 재현하고, PG 환불 실패는 포트 스텁으로 주입한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PaymentReconciliationFaultTest extends BatchIntegrationTest {

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final PendingOrderSweepFacade pendingOrderSweepFacade;
    private final MemberAppender memberAppender;
    private final OrderAppender orderAppender;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final StockModifier stockModifier;
    private final StockReader stockReader;
    private final PaymentAppender paymentAppender;
    private final PaymentProcessor paymentProcessor;
    private final PaymentReader paymentReader;
    private final ProductVariantReader variantReader;
    private final JdbcTemplate jdbcTemplate;

    PaymentReconciliationFaultTest(
            PaymentConfirmationFacade paymentConfirmationFacade,
            PendingOrderSweepFacade pendingOrderSweepFacade,
            MemberAppender memberAppender,
            OrderAppender orderAppender,
            OrderModifier orderModifier,
            OrderReader orderReader,
            StockModifier stockModifier,
            StockReader stockReader,
            PaymentAppender paymentAppender,
            PaymentProcessor paymentProcessor,
            PaymentReader paymentReader,
            ProductVariantReader variantReader,
            JdbcTemplate jdbcTemplate) {
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.pendingOrderSweepFacade = pendingOrderSweepFacade;
        this.memberAppender = memberAppender;
        this.orderAppender = orderAppender;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
        this.paymentAppender = paymentAppender;
        this.paymentProcessor = paymentProcessor;
        this.paymentReader = paymentReader;
        this.variantReader = variantReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("승인 커밋과 결제완료 사이 중단(APPROVED × PENDING)은 다음 PENDING 스윕이 결제완료로 수렴시킨다")
    void approvedPaymentWithPendingOrderConvergesOnNextSweep() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        // 사각지대 상태: 돈은 빠졌는데(payment APPROVED) 주문은 PENDING이다.
        InterruptedCheckout state = approvedPaymentWithPendingOrder(memberId, variantId, 2, Money.of(20000L));

        agePastPaymentStaleAfter(state.paymentId());
        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentReader.getPayment(state.paymentId()).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("고아 청구 환불이 실패하면 결제가 REQUESTED로 남고 다음 리컨실 스윕이 환불을 완결한다")
    void orphanedChargeRefundFailureStaysRequestedAndConvergesOnNextSweep() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(5000L), 50);
        InterruptedCheckout state = orphanedChargeOfCancelledOrder(memberId, variantId, 2, Money.of(10000L));
        doThrow(new IllegalStateException("PG 환불 실패 주입")).when(paymentGateway).cancel(anyString(), anyString());

        paymentConfirmationFacade.reconcile(Instant.now());

        // 환불이 실패해도 승인 기록이 커밋되지 않아 REQUESTED로 남는다 — 리컨실 대상에서 이탈하지 않는다.
        assertThat(paymentReader.getPayment(state.paymentId()).status()).isEqualTo(PaymentStatus.REQUESTED);

        doCallRealMethod().when(paymentGateway).cancel(anyString(), anyString());
        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo refunded = paymentReader.getPayment(state.paymentId());
        assertThat(refunded.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refunded.pgTransactionId()).isNotNull();
        assertThat(refunded.pgCancelTransactionId()).isNotNull();
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = seedOnSaleProduct(price, quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    /**
     * 주문 PENDING·재고 차감·차감 완료 마커·결제 REQUESTED까지 진행되고 승인 결과를 기록하지 못한 상태를
     * 재현한다. 체크아웃과 같은 순서(차감 → 마커 → 결제 요청)다.
     */
    private InterruptedCheckout interruptCheckoutBeforeApproval(
            UUID memberId, UUID variantId, int quantity, Money payAmount) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        OrderLineSnapshot snapshot = new OrderLineSnapshot(
                variant.id(), variant.productId(), "상품", variant.optionLabel(), variant.price(), quantity);
        UUID orderId = orderAppender.place(memberId, List.of(snapshot), address(), Money.ZERO, Money.ZERO, null);
        stockModifier.deduct(variantId, quantity);
        orderModifier.markStockDeducted(orderId);
        UUID paymentId = paymentAppender.request(orderId, payAmount, PaymentMethod.CARD);
        return new InterruptedCheckout(orderId, paymentId);
    }

    /** 승인 커밋 직후·주문 결제완료 전 중단이 남긴 잔여(payment APPROVED × order PENDING)를 재현한다. */
    private InterruptedCheckout approvedPaymentWithPendingOrder(
            UUID memberId, UUID variantId, int quantity, Money payAmount) {
        InterruptedCheckout checkout = interruptCheckoutBeforeApproval(memberId, variantId, quantity, payAmount);
        PaymentApproval approval = paymentGateway.approve(checkout.paymentId(), payAmount, PaymentMethod.CARD);
        paymentProcessor.confirmApproval(checkout.paymentId(), Objects.requireNonNull(approval.pgTransactionId()));
        return checkout;
    }

    /** 승인이 PG에 기록된 채 응답이 유실돼 동기 체크아웃 보상이 취소·복원까지 마친 고아 청구 상태를 재현한다. */
    private InterruptedCheckout orphanedChargeOfCancelledOrder(
            UUID memberId, UUID variantId, int quantity, Money payAmount) {
        InterruptedCheckout checkout = interruptCheckoutBeforeApproval(memberId, variantId, quantity, payAmount);
        paymentGateway.approve(checkout.paymentId(), payAmount, PaymentMethod.CARD);
        orderModifier.cancel(checkout.orderId(), CancellationReason.PAYMENT_FAILED);
        stockModifier.restore(variantId, quantity);
        return checkout;
    }

    private record InterruptedCheckout(UUID orderId, UUID paymentId) {}

    /** 결제 생성 시각을 확정 경로 유예({@code payment.reconciliation.stale-after}) 이전으로 되돌린다. */
    private void agePastPaymentStaleAfter(UUID paymentId) {
        jdbcTemplate.update(
                "UPDATE payment.payment SET created_at = created_at - INTERVAL '1 hour' WHERE id = ?", paymentId);
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

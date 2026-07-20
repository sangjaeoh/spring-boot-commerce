package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.commerce.cart.service.CartAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.payment.service.PaymentReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 리컨실 사각지대를 크래시 주입으로 재현하고 다음 스윕 수렴을 검증하는 테스트다.
 *
 * <p>(1) 결제 승인 커밋과 주문 결제완료({@code markPaid}) 사이 중단 — payment=APPROVED·order=PENDING 잔여를
 * PENDING 스윕이 발견해 결제 리컨실이 결제완료로 완결한다. (2) 고아 청구 환불의 PG 환불 실패 — 승인 기록이
 * 커밋되지 않아 결제가 REQUESTED로 남고(리컨실 대상 이탈 없음) 다음 스윕이 환불을 완결한다(자기복구).
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PaymentReconciliationFaultTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final PendingOrderSweepFacade pendingOrderSweepFacade;
    private final CheckoutFacade checkoutFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final OrderReader orderReader;
    private final StockReader stockReader;
    private final PaymentReader paymentReader;
    private final ProductVariantReader variantReader;
    private final JdbcTemplate jdbcTemplate;

    PaymentReconciliationFaultTest(
            PaymentConfirmationFacade paymentConfirmationFacade,
            PendingOrderSweepFacade pendingOrderSweepFacade,
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            OrderReader orderReader,
            StockReader stockReader,
            PaymentReader paymentReader,
            ProductVariantReader variantReader,
            JdbcTemplate jdbcTemplate) {
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.pendingOrderSweepFacade = pendingOrderSweepFacade;
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.orderReader = orderReader;
        this.stockReader = stockReader;
        this.paymentReader = paymentReader;
        this.variantReader = variantReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("승인 커밋과 결제완료 사이 중단(APPROVED × PENDING)은 다음 PENDING 스윕이 결제완료로 수렴시킨다")
    void approvedPaymentWithPendingOrderConvergesOnNextSweep() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        doThrow(new IllegalStateException("markPaid 전 중단 주입"))
                .when(orderModifier)
                .markPaid(any(UUID.class));

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class);

        OrderInfo order = orderReader
                .getOrdersByMember(memberId, PageRequest.of(0, 1))
                .getContent()
                .get(0);
        PaymentInfo payment = paymentReader.getByOrderId(order.id());
        // 사각지대 상태: 돈은 빠졌는데(payment APPROVED) 주문은 PENDING이다.
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);

        doCallRealMethod().when(orderModifier).markPaid(any(UUID.class));
        agePastPaymentStaleAfter(payment.id());
        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(order.id()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentReader.getPayment(payment.id()).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("고아 청구 환불이 실패하면 결제가 REQUESTED로 남고 다음 리컨실 스윕이 환불을 완결한다")
    void orphanedChargeRefundFailureStaysRequestedAndConvergesOnNextSweep() {
        UUID memberId = registerMember();
        // 5,499 × 2 = 10,998 — fake PG 응답 유실 트리거(끝 세 자리 998): 승인은 PG에 남고 체크아웃은 예외로 보상한다
        UUID variantId = seedProduct(Money.of(5499L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOf(RuntimeException.class);
        OrderInfo order = orderReader
                .getOrdersByMember(memberId, PageRequest.of(0, 1))
                .getContent()
                .get(0);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        PaymentInfo payment = paymentReader.getByOrderId(order.id());
        doThrow(new IllegalStateException("PG 환불 실패 주입")).when(paymentGateway).cancel(anyString(), anyString());

        paymentConfirmationFacade.reconcile(Instant.now());

        // 환불이 실패해도 승인 기록이 커밋되지 않아 REQUESTED로 남는다 — 리컨실 대상에서 이탈하지 않는다.
        assertThat(paymentReader.getPayment(payment.id()).status()).isEqualTo(PaymentStatus.REQUESTED);

        doCallRealMethod().when(paymentGateway).cancel(anyString(), anyString());
        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo refunded = paymentReader.getPayment(payment.id());
        assertThat(refunded.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refunded.pgTransactionId()).isNotNull();
        assertThat(refunded.pgCancelTransactionId()).isNotNull();
        assertThat(orderReader.getOrder(order.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, price, List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    /** 결제 생성 시각을 확정 경로 유예({@code payment.reconciliation.stale-after}) 이전으로 되돌린다. */
    private void agePastPaymentStaleAfter(UUID paymentId) {
        jdbcTemplate.update(
                "UPDATE payment.payment SET created_at = created_at - INTERVAL '1 hour' WHERE id = ?", paymentId);
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

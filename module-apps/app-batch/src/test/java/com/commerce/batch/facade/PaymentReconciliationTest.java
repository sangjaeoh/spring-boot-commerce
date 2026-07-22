package com.commerce.batch.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.batch.BatchIntegrationTest;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentReader;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * 응답 유실 결제의 리컨실 확정 경로를 검증하는 테스트다.
 *
 * <p>동기 체크아웃이 결과를 기록하지 못한 채 중단된 상태(주문 PENDING·재고 차감·결제 REQUESTED)를 도메인
 * 서비스 직접 호출로 재현하고 — PG 응답 유실은 포트 승인을 부른 뒤 반환을 버리는 것으로 재현한다 — PG측
 * 거래 유무에 따라 리컨실이 승인 확정 또는 보상을 태우는지, 반복 실행이 멱등한지 확인한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PaymentReconciliationTest extends BatchIntegrationTest {

    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final MemberAppender memberAppender;
    private final OrderAppender orderAppender;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final StockModifier stockModifier;
    private final StockReader stockReader;
    private final PaymentAppender paymentAppender;
    private final PaymentReader paymentReader;
    private final PaymentGateway paymentGateway;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponModifier issuedCouponModifier;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;

    PaymentReconciliationTest(
            PaymentConfirmationFacade paymentConfirmationFacade,
            MemberAppender memberAppender,
            OrderAppender orderAppender,
            OrderModifier orderModifier,
            OrderReader orderReader,
            StockModifier stockModifier,
            StockReader stockReader,
            PaymentAppender paymentAppender,
            PaymentReader paymentReader,
            PaymentGateway paymentGateway,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader) {
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.memberAppender = memberAppender;
        this.orderAppender = orderAppender;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
        this.paymentAppender = paymentAppender;
        this.paymentReader = paymentReader;
        this.paymentGateway = paymentGateway;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("PG 승인 응답이 유실된 결제를 리컨실이 승인 확정하고 주문을 PAID로 전이하며 반복 실행은 멱등하다")
    void reconcileConfirmsLostApprovalAndMarksOrderPaid() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        InterruptedCheckout state = interruptCheckoutBeforeApproval(memberId, variantId, 2, Money.of(20000L));
        paymentGateway.approve(state.paymentId(), Money.of(20000L), PaymentMethod.CARD);

        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo payment = paymentReader.getPayment(state.paymentId());
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.pgTransactionId()).isNotNull();
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);

        paymentConfirmationFacade.reconcile(Instant.now());

        assertThat(paymentReader.getPayment(state.paymentId()).pgTransactionId())
                .isEqualTo(payment.pgTransactionId());
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("청구가 PG에 도달하지 않은 결제를 리컨실이 실패 확정하고 재고·쿠폰을 복원하며 반복 실행은 이중 복원하지 않는다")
    void reconcileCompensatesUnreachedChargeIdempotently() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        InterruptedCheckout state =
                interruptCheckoutBeforeApproval(memberId, variantId, 2, Money.of(18000L), issuedId, Money.of(2000L));

        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo payment = paymentReader.getPayment(state.paymentId());
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).isEqualTo(FailureReason.GATEWAY_ERROR);
        OrderInfo order = orderReader.getOrder(state.orderId());
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.PAYMENT_FAILED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);

        paymentConfirmationFacade.reconcile(Instant.now());

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("PG가 거절한 채 응답이 유실된 결제를 리컨실이 거절 사유로 실패 확정하고 보상한다")
    void reconcileConfirmsLostDeclineWithGatewayReason() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(9999L), 50);
        InterruptedCheckout state = interruptCheckoutBeforeApproval(memberId, variantId, 1, Money.of(9999L));
        paymentGateway.approve(state.paymentId(), Money.of(9999L), PaymentMethod.CARD);

        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo payment = paymentReader.getPayment(state.paymentId());
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("지연 승인 전에 주문이 동기 보상으로 취소됐으면 리컨실이 고아 청구를 환불한다")
    void reconcileRefundsOrphanedChargeOfCancelledOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(5000L), 50);
        // 승인은 PG에 남고 응답이 유실돼 동기 체크아웃 보상이 주문 취소·재고 복원까지 마친 고아 청구 상태를 재현한다
        InterruptedCheckout state = interruptCheckoutBeforeApproval(memberId, variantId, 2, Money.of(10000L));
        paymentGateway.approve(state.paymentId(), Money.of(10000L), PaymentMethod.CARD);
        orderModifier.cancel(state.orderId(), CancellationReason.PAYMENT_FAILED);
        stockModifier.restore(variantId, 2);

        paymentConfirmationFacade.reconcile(Instant.now());

        PaymentInfo payment = paymentReader.getPayment(state.paymentId());
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.pgTransactionId()).isNotNull();
        assertThat(payment.pgCancelTransactionId()).isNotNull();
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

    private InterruptedCheckout interruptCheckoutBeforeApproval(
            UUID memberId, UUID variantId, int quantity, Money payAmount) {
        return interruptCheckoutBeforeApproval(memberId, variantId, quantity, payAmount, null, Money.ZERO);
    }

    /**
     * 주문 PENDING·전 라인 재고 차감·차감 완료 마커·(쿠폰 확정·)결제 REQUESTED까지 진행되고 승인 결과를
     * 기록하지 못한 상태를 재현한다. 체크아웃과 같은 순서(차감 → 마커 → 쿠폰 → 결제 요청)다.
     */
    private InterruptedCheckout interruptCheckoutBeforeApproval(
            UUID memberId,
            UUID variantId,
            int quantity,
            Money payAmount,
            @Nullable UUID issuedCouponId,
            Money discountAmount) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        OrderLineSnapshot snapshot = new OrderLineSnapshot(
                variant.id(), variant.productId(), "상품", variant.optionLabel(), variant.price(), quantity);
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot), address(), discountAmount, Money.ZERO, issuedCouponId);
        stockModifier.deduct(variantId, quantity);
        orderModifier.markStockDeducted(orderId);
        if (issuedCouponId != null) {
            issuedCouponModifier.use(issuedCouponId, memberId, orderId);
        }
        UUID paymentId = paymentAppender.request(orderId, payAmount, PaymentMethod.CARD);
        return new InterruptedCheckout(orderId, paymentId);
    }

    private record InterruptedCheckout(UUID orderId, UUID paymentId) {}

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

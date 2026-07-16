package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberRemover;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentReader;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * payment 행 없는 미결제(PENDING) 주문의 스윕 보상 종결을 검증한다.
 *
 * <p>주문 생성~결제 요청 이전 구간에서 체크아웃이 중단된 상태(주문 PENDING·재고 차감·(쿠폰 확정·)payment 행
 * 없음)를 도메인 서비스 직접 호출로 재현하고 — 유예 전 미개입, payment 행 유무에 따른 관할 분기, 반복 실행
 * 멱등, 탈퇴 회원 주문 종결을 확인한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PendingOrderSweepTest extends FacadeIntegrationTest {

    private final PendingOrderSweepFacade pendingOrderSweepFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final MemberRemover memberRemover;
    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final StockModifier stockModifier;
    private final StockReader stockReader;
    private final PaymentAppender paymentAppender;
    private final PaymentReader paymentReader;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponModifier issuedCouponModifier;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;

    PendingOrderSweepTest(
            PendingOrderSweepFacade pendingOrderSweepFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            MemberRemover memberRemover,
            OrderAppender orderAppender,
            OrderReader orderReader,
            StockModifier stockModifier,
            StockReader stockReader,
            PaymentAppender paymentAppender,
            PaymentReader paymentReader,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader) {
        this.pendingOrderSweepFacade = pendingOrderSweepFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.memberRemover = memberRemover;
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
        this.paymentAppender = paymentAppender;
        this.paymentReader = paymentReader;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("유예가 지나지 않은 PENDING 주문은 스윕이 건드리지 않는다")
    void doesNotSweepWithinGrace() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.ZERO, null);

        // cutoff를 과거로 두면 방금 만든 주문은 cutoff 이전이 아니라 대상에서 빠진다(유예 미경과 재현).
        pendingOrderSweepFacade.reconcile(Instant.now().minus(Duration.ofHours(1)));

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("payment 행 없는 유예 경과 PENDING 주문을 스윕이 취소하고 재고·쿠폰을 복원한다")
    void sweepsAndCompensatesPendingWithoutPayment() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.of(2000L), issuedId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.CHECKOUT_ABANDONED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("payment 행이 있는 PENDING 주문은 결제 리컨실 관할이라 스윕이 건드리지 않는다")
    void skipsPendingWithPaymentRow() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.ZERO, null);
        UUID paymentId = paymentAppender.request(orderId, Money.of(20000L), PaymentMethod.CARD);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
        assertThat(paymentReader.getPayment(paymentId).status()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    @DisplayName("스윕 반복 실행은 재고를 정확히 한 번만 복원한다")
    void repeatedSweepRestoresExactlyOnce() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID orderId = placeInterruptedPending(memberId, variantId, 3, Money.ZERO, null);

        pendingOrderSweepFacade.reconcile(Instant.now());
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);

        pendingOrderSweepFacade.reconcile(Instant.now());
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("탈퇴 회원의 payment 행 없는 PENDING 주문도 스윕이 종결한다")
    void sweepsWithdrawnMembersPendingOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.ZERO, null);
        memberRemover.delete(memberId, WithdrawalReason.NO_LONGER_USED);

        pendingOrderSweepFacade.reconcile(Instant.now());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.CHECKOUT_ABANDONED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, price, List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    /** 주문 PENDING·재고 차감·(쿠폰 확정·)payment 행 없음까지 진행되고 결제 요청 이전에 중단된 상태를 재현한다. */
    private UUID placeInterruptedPending(
            UUID memberId, UUID variantId, int quantity, Money discountAmount, @Nullable UUID issuedCouponId) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        OrderLineSnapshot snapshot = new OrderLineSnapshot(
                variant.id(), variant.productId(), "상품", variant.optionLabel(), variant.price(), quantity);
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot), address(), discountAmount, Money.ZERO, issuedCouponId);
        stockModifier.deduct(variantId, quantity);
        if (issuedCouponId != null) {
            issuedCouponModifier.use(issuedCouponId, orderId);
        }
        return orderId;
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

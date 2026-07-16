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
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentProcessor;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

/**
 * 미결제(PENDING) 주문 스윕의 관할 분기를 검증한다 — payment 행 없는 잔여의 직접 보상 종결과, 종결
 * 기록된(비REQUESTED) 결제가 남긴 잔여의 결제 리컨실 위임.
 *
 * <p>체크아웃이 중간에 중단된 상태(주문 PENDING·재고 차감·(쿠폰 확정·)payment 행 없음/기록됨)를 도메인
 * 서비스 직접 호출로 재현하고 — 유예 전 미개입, payment 행 유무·상태에 따른 관할 분기, 반복 실행 멱등,
 * 탈퇴 회원 주문 종결을 확인한다. 재고 복원의 차감 완료 마커 게이트는 크래시 타이밍별(차감 전·라인 중간·
 * 차감 완료 후)로 재고 증식이 없음을 확인한다. 위임 대상 결제는 확정 경로의 유예({@code stale-after})가
 * 지나야 하므로 생성 시각을 SQL로 과거로 되돌려 재현한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PendingOrderSweepTest extends FacadeIntegrationTest {

    private final PendingOrderSweepFacade pendingOrderSweepFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final MemberRemover memberRemover;
    private final OrderAppender orderAppender;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final StockModifier stockModifier;
    private final StockReader stockReader;
    private final PaymentAppender paymentAppender;
    private final PaymentProcessor paymentProcessor;
    private final PaymentReader paymentReader;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponModifier issuedCouponModifier;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;
    private final JdbcTemplate jdbcTemplate;

    PendingOrderSweepTest(
            PendingOrderSweepFacade pendingOrderSweepFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            MemberRemover memberRemover,
            OrderAppender orderAppender,
            OrderModifier orderModifier,
            OrderReader orderReader,
            StockModifier stockModifier,
            StockReader stockReader,
            PaymentAppender paymentAppender,
            PaymentProcessor paymentProcessor,
            PaymentReader paymentReader,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader,
            JdbcTemplate jdbcTemplate) {
        this.pendingOrderSweepFacade = pendingOrderSweepFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.memberRemover = memberRemover;
        this.orderAppender = orderAppender;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
        this.paymentAppender = paymentAppender;
        this.paymentProcessor = paymentProcessor;
        this.paymentReader = paymentReader;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
        this.jdbcTemplate = jdbcTemplate;
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
    @DisplayName("차감 전 크래시 잔여(차감 완료 증거 없음)는 스윕이 취소만 하고 재고를 복원하지 않는다")
    void doesNotRestoreStockWhenCrashedBeforeDeduction() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        // 주문 PENDING 커밋 직후(재고 차감 전) 크래시 — 차감도 마커도 없다.
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot(variantId, 2)), address(), Money.ZERO, Money.ZERO, null);

        pendingOrderSweepFacade.reconcile(Instant.now());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.CHECKOUT_ABANDONED);
        // 차감된 적 없는 재고가 부풀지 않는다(52가 아니라 50).
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("라인 중간 크래시 잔여(차감 완료 증거 없음)는 스윕이 복원을 생략해 어떤 라인도 부풀지 않는다")
    void doesNotRestoreStockWhenCrashedMidDeduction() {
        UUID memberId = registerMember();
        UUID firstVariantId = seedProduct(Money.of(10000L), 50);
        UUID secondVariantId = seedProduct(Money.of(20000L), 50);
        UUID orderId = orderAppender.place(
                memberId,
                List.of(snapshot(firstVariantId, 2), snapshot(secondVariantId, 3)),
                address(),
                Money.ZERO,
                Money.ZERO,
                null);
        // 첫 라인만 차감하고 크래시 — 전 라인 차감이 아니라 마커가 없다.
        stockModifier.deduct(firstVariantId, 2);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        // 차감된 라인은 복원 생략으로 팬텀 품절로 남고(운영 대사 대상), 차감 안 된 라인은 부풀지 않는다.
        assertThat(stockReader.getByVariantId(firstVariantId).quantity()).isEqualTo(48);
        assertThat(stockReader.getByVariantId(secondVariantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("REQUESTED payment 행이 있는 PENDING 주문은 결제 리컨실 관할이라 스윕이 건드리지 않는다")
    void skipsPendingWithRequestedPaymentRow() {
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
    @DisplayName("승인 기록 후 결제완료 전 중단된(APPROVED × PENDING) 주문을 스윕이 결제 리컨실에 위임해 결제완료로 완결한다")
    void delegatesApprovedPaymentPendingOrderAndCompletesPaid() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.of(2000L), issuedId);
        UUID paymentId = paymentAppender.request(orderId, Money.of(18000L), PaymentMethod.CARD);
        paymentProcessor.approve(paymentId);
        agePastPaymentStaleAfter(paymentId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentReader.getPayment(paymentId).status()).isEqualTo(PaymentStatus.APPROVED);
        // 보상이 아니라 완결이다 — 차감 재고·사용 쿠폰은 그대로 남는다.
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.USED);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("거절 기록 후 보상 취소가 실패한(FAILED × PENDING) 주문을 스윕이 결제 리컨실에 위임해 보상 종결한다")
    void delegatesFailedPaymentPendingOrderAndCompensates() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = placeInterruptedPending(memberId, variantId, 2, Money.of(2000L), issuedId);
        UUID paymentId = paymentAppender.request(orderId, Money.of(18000L), PaymentMethod.CARD);
        paymentProcessor.confirmFailure(paymentId, FailureReason.INSUFFICIENT_BALANCE);
        agePastPaymentStaleAfter(paymentId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.PAYMENT_FAILED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(paymentReader.getPayment(paymentId).status()).isEqualTo(PaymentStatus.FAILED);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("차감 완료 증거 없는 FAILED × PENDING 잔여는 리컨실 위임 보상도 재고를 복원하지 않는다")
    void delegatedCompensationSkipsRestoreWithoutDeductionEvidence() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        // 체크아웃은 마커 커밋 뒤에만 payment 행을 만들므로 모델상 도달 불가지만, 리컨실 보상 경로의
        // 증거 게이트가 지역 강제됨을 고정한다 — 마커 없이 차감·FAILED 기록만 있는 상태를 인위 구성한다.
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot(variantId, 2)), address(), Money.ZERO, Money.ZERO, null);
        stockModifier.deduct(variantId, 2);
        UUID paymentId = paymentAppender.request(orderId, Money.of(20000L), PaymentMethod.CARD);
        paymentProcessor.confirmFailure(paymentId, FailureReason.INSUFFICIENT_BALANCE);
        agePastPaymentStaleAfter(paymentId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancellationReason()).isEqualTo(CancellationReason.PAYMENT_FAILED);
        // 증거가 없으므로 복원 생략 — 차감분은 팬텀 품절로 남고(운영 대사 대상) 재고는 부풀지 않는다.
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
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

    /** 결제 생성 시각을 확정 경로 유예({@code payment.reconciliation.stale-after}) 이전으로 되돌린다. */
    private void agePastPaymentStaleAfter(UUID paymentId) {
        jdbcTemplate.update(
                "UPDATE payment.payment SET created_at = created_at - INTERVAL '1 hour' WHERE id = ?", paymentId);
    }

    /**
     * 주문 PENDING·전 라인 재고 차감·차감 완료 마커·(쿠폰 확정·)payment 행 없음까지 진행되고 결제 요청
     * 이전에 중단된 상태를 재현한다. 체크아웃과 같은 순서(차감 → 마커 → 쿠폰)다.
     */
    private UUID placeInterruptedPending(
            UUID memberId, UUID variantId, int quantity, Money discountAmount, @Nullable UUID issuedCouponId) {
        UUID orderId = orderAppender.place(
                memberId,
                List.of(snapshot(variantId, quantity)),
                address(),
                discountAmount,
                Money.ZERO,
                issuedCouponId);
        stockModifier.deduct(variantId, quantity);
        orderModifier.markStockDeducted(orderId);
        if (issuedCouponId != null) {
            issuedCouponModifier.use(issuedCouponId, memberId, orderId);
        }
        return orderId;
    }

    private OrderLineSnapshot snapshot(UUID variantId, int quantity) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        return new OrderLineSnapshot(
                variant.id(), variant.productId(), "상품", variant.optionLabel(), variant.price(), quantity);
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

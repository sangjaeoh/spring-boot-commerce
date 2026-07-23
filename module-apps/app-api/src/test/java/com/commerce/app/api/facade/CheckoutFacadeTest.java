package com.commerce.app.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.app.api.exception.ApiErrorCode;
import com.commerce.app.api.exception.ApiException;
import com.commerce.domain.cart.application.provided.CartAppender;
import com.commerce.domain.coupon.application.provided.CouponAppender;
import com.commerce.domain.coupon.application.provided.IssuedCouponAppender;
import com.commerce.domain.coupon.application.provided.IssuedCouponModifier;
import com.commerce.domain.coupon.application.provided.IssuedCouponReader;
import com.commerce.domain.coupon.domain.Discount;
import com.commerce.domain.coupon.domain.IssuedCouponStatus;
import com.commerce.domain.coupon.domain.ValidityPeriod;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.member.application.provided.MemberModifier;
import com.commerce.domain.member.domain.SuspensionReason;
import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.product.application.provided.ProductModifier;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockReader;
import com.commerce.event.order.OrderPaid;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutFacadeTest extends FacadeIntegrationTest {

    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponModifier issuedCouponModifier;
    private final OrderReader orderReader;
    private final ProductModifier productModifier;
    private final ProductVariantReader variantReader;
    private final IssuedCouponReader issuedCouponReader;
    private final StockReader stockReader;
    private final JdbcTemplate jdbcTemplate;

    CheckoutFacadeTest(
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponModifier issuedCouponModifier,
            OrderReader orderReader,
            ProductModifier productModifier,
            ProductVariantReader variantReader,
            IssuedCouponReader issuedCouponReader,
            StockReader stockReader,
            JdbcTemplate jdbcTemplate) {
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponModifier = issuedCouponModifier;
        this.orderReader = orderReader;
        this.productModifier = productModifier;
        this.variantReader = variantReader;
        this.issuedCouponReader = issuedCouponReader;
        this.stockReader = stockReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("체크아웃이 주문을 결제하고 재고를 차감하며 커밋과 함께 OrderPaid를 아웃박스에 남긴다")
    // 장바구니 비우기 소비는 아웃박스 릴레이(app-batch) 소유다 — 여기서는 발행의 원자적 영속만 검증한다.
    void checkoutPaysOrderDeductsStockAndRecordsOutboxEvent() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);

        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.of(3000L), null, PaymentMethod.CARD);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(order.payAmount()).isEqualTo(Money.of(23000L));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
        Long outboxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messaging.outbox WHERE event_type = ? AND payload LIKE ?",
                Long.class,
                OrderPaid.EVENT_TYPE,
                "%" + orderId + "%");
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 체크아웃이 할인을 적용하고 발급분을 USED로 전이한다")
    void checkoutWithCouponAppliesDiscountAndUsesCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);

        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.discountAmount()).isEqualTo(Money.of(2000L));
        assertThat(order.payAmount()).isEqualTo(Money.of(18000L));
        assertThat(order.issuedCouponId()).isEqualTo(issuedId);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.USED);
    }

    @Test
    @DisplayName("무효화된 쿠폰 체크아웃은 적용 불가로 거부되고 재고를 차감하지 않는다")
    void checkoutRejectsRevokedCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        issuedCouponModifier.revoke(issuedId, "오발급 회수");

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.COUPON_NOT_APPLICABLE));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.REVOKED);
    }

    @Test
    @DisplayName("빈 장바구니 체크아웃은 거부된다")
    void checkoutRejectsEmptyCart() {
        UUID memberId = registerMember();

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.EMPTY_CART));
    }

    @Test
    @DisplayName("정지 회원 체크아웃은 자격 없음으로 거부된다")
    void checkoutRejectsSuspendedMember() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.MEMBER_NOT_ELIGIBLE));
    }

    @Test
    @DisplayName("담긴 뒤 숨겨진 상품 라인의 체크아웃은 주문 불가로 거부된다")
    void checkoutRejectsHiddenProductLine() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 5);
        cartAppender.addItem(memberId, variantId, 1);
        productModifier.hide(variantReader.getVariant(variantId).productId());

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_ORDERABLE));
    }

    @Test
    @DisplayName("재고 부족 체크아웃은 거부되고 주문·재고를 만들지 않는다")
    void checkoutRejectsInsufficientStock() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 1);
        cartAppender.addItem(memberId, variantId, 5);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INSUFFICIENT_STOCK));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(1);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = seedOnSaleProduct("상품", null, price, quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

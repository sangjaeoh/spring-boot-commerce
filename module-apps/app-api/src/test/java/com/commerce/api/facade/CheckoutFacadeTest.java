package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutFacadeTest extends FacadeIntegrationTest {

    private final CheckoutFacade checkoutFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final OrderReader orderReader;
    private final CartReader cartReader;
    private final ProductVariantReader variantReader;
    private final IssuedCouponReader issuedCouponReader;
    private final StockReader stockReader;

    CheckoutFacadeTest(
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            OrderReader orderReader,
            CartReader cartReader,
            ProductVariantReader variantReader,
            IssuedCouponReader issuedCouponReader,
            StockReader stockReader) {
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.orderReader = orderReader;
        this.cartReader = cartReader;
        this.variantReader = variantReader;
        this.issuedCouponReader = issuedCouponReader;
        this.stockReader = stockReader;
    }

    @Test
    @DisplayName("체크아웃이 주문을 결제하고 재고를 차감하며 커밋 후 장바구니를 비운다")
    void checkoutPaysOrderDeductsStockAndClearsCart() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);

        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.of(3000L), null, PaymentMethod.CARD);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(order.payAmount()).isEqualTo(Money.of(23000L));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
        assertThat(cartReader.getCart(memberId).items()).isEmpty();
    }

    @Test
    @DisplayName("쿠폰 체크아웃이 할인을 적용하고 발급분을 USED로 전이한다")
    void checkoutWithCouponAppliesDiscountAndUsesCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30);
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
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, price, List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

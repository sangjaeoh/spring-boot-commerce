package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderModifier;
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
class OrderCancellationFacadeTest extends FacadeIntegrationTest {

    private final OrderCancellationFacade orderCancellationFacade;
    private final CheckoutFacade checkoutFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final OrderReader orderReader;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;
    private final OrderModifier orderModifier;

    OrderCancellationFacadeTest(
            OrderCancellationFacade orderCancellationFacade,
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            OrderReader orderReader,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader,
            OrderModifier orderModifier) {
        this.orderCancellationFacade = orderCancellationFacade;
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.orderReader = orderReader;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
        this.orderModifier = orderModifier;
    }

    @Test
    @DisplayName("결제완료 주문 취소가 주문을 취소하고 재고·쿠폰을 복원한다")
    void cancelRestoresStockAndCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId = couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        orderCancellationFacade.cancel(orderId);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("출고된 주문 취소는 거부되고 재고를 복원하지 않는다")
    void cancelRejectsShippedOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        orderModifier.ship(orderId);

        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.ORDER_NOT_CANCELLABLE));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(49);
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

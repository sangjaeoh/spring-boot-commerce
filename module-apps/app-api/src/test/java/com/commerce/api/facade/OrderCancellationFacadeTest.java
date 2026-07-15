package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderStatusException;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.exception.StockShortageException;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 완료 주문의 취소·복원과 그 재시도 멱등을 통합 검증한다.
 *
 * <p>롤백 없는 통합 하네스를 상속해 각 도메인 서비스가 실제로 커밋하므로, 한 메서드 안의 파사드 재시도가 시도
 * 간 실 영속 상태를 본다. 다운스트림 실패는 spy에 doThrow로 1회 주입하고 재시도 직전 {@link Mockito#reset}으로
 * 실제 위임으로 되돌린다. PaymentGateway를 spy로 감싸 환불이 두 시도에 걸쳐 정확히 한 번만 일어나는지 관측한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderCancellationFacadeTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    @MockitoSpyBean
    private StockModifier stockModifier;

    @MockitoSpyBean
    private IssuedCouponModifier issuedCouponModifier;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

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
            ProductVariantReader variantReader) {
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

    @Test
    @DisplayName("주문 취소 실패로 환불만 커밋된 뒤 재시도가 이미 취소된 결제를 관용해 복원을 완결한다")
    void retryToleratesCancelledPaymentWhenOrderCancelFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId = couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION))
                .when(orderModifier)
                .cancel(eq(orderId), any());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId)).isInstanceOf(OrderStatusException.class);
        Mockito.reset(orderModifier);

        orderCancellationFacade.cancel(orderId);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("재고 복원 실패 후 재시도가 CANCELLED 주문을 관용해 재고를 정확히 한 번 복원한다")
    void retryCompletesRestoreWhenStockRestoreFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId = couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new StockShortageException(StockErrorCode.STOCK_SHORTAGE))
                .when(stockModifier)
                .restore(eq(variantId), anyInt());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId)).isInstanceOf(StockShortageException.class);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        Mockito.reset(stockModifier);

        orderCancellationFacade.cancel(orderId);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("쿠폰 복원 실패 후 재시도가 재고를 이중 복원하지 않고 정확히 한 번 복원한다")
    void retryCompletesWhenCouponRestoreFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId = couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_USABLE))
                .when(issuedCouponModifier)
                .restoreUse(eq(issuedId));
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId)).isInstanceOf(CouponStatusException.class);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        Mockito.reset(issuedCouponModifier);

        orderCancellationFacade.cancel(orderId);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
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

package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.exception.OrderErrorCode;
import com.commerce.order.exception.OrderStatusException;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.payment.service.PaymentReader;
import com.commerce.product.service.ProductVariantReader;
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
 * 배송 완료 주문의 전체 반품 환불과 그 재시도 멱등을 통합 검증한다.
 *
 * <p>취소 파사드 테스트와 같은 롤백 없는 통합 하네스다. PaymentGateway를 spy로 감싸 PG 환불이 중복
 * 호출·재시도에 걸쳐 정확히 한 번만 일어나는지 관측한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderRefundFacadeTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private final OrderRefundFacade orderRefundFacade;
    private final OrderCancellationFacade orderCancellationFacade;
    private final CheckoutFacade checkoutFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;

    OrderRefundFacadeTest(
            OrderRefundFacade orderRefundFacade,
            OrderCancellationFacade orderCancellationFacade,
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            OrderReader orderReader,
            PaymentReader paymentReader,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader) {
        this.orderRefundFacade = orderRefundFacade;
        this.orderCancellationFacade = orderCancellationFacade;
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("배송 완료 주문 환불이 주문을 REFUNDED로 전이하고 결제를 취소하며 재고·쿠폰을 복원한다")
    void refundTransitionsOrderCancelsPaymentAndRestores() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = deliveredCheckout(memberId, issuedId);

        orderRefundFacade.refund(orderId, RefundReason.PRODUCT_DEFECT);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.refundedAt()).isNotNull();
        assertThat(order.refundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.pgCancelTransactionId()).isNotNull();
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("배송 완료 전(준비·출고) 주문 환불은 거부되고 재고를 복원하지 않는다")
    void refundRejectsUndeliveredOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        assertThatThrownBy(() -> orderRefundFacade.refund(orderId, RefundReason.CHANGE_OF_MIND))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.ORDER_NOT_REFUNDABLE));

        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        assertThatThrownBy(() -> orderRefundFacade.refund(orderId, RefundReason.CHANGE_OF_MIND))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.ORDER_NOT_REFUNDABLE));

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(49);
    }

    @Test
    @DisplayName("취소된 주문 환불은 거부되고 재고를 이중 복원하지 않는다")
    void refundRejectsCancelledOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        orderCancellationFacade.cancel(orderId, memberId);

        assertThatThrownBy(() -> orderRefundFacade.refund(orderId, RefundReason.CHANGE_OF_MIND))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.ORDER_NOT_REFUNDABLE));

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("환불 완결 후 재호출해도 PG 환불은 한 번만 일어나고 재고·쿠폰을 추가로 건드리지 않는다")
    void duplicateRefundDoesNotRefundTwice() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = deliveredCheckout(memberId, issuedId);

        orderRefundFacade.refund(orderId, RefundReason.PRODUCT_DEFECT);
        String firstCancelTransactionId = paymentReader.getByOrderId(orderId).pgCancelTransactionId();
        orderRefundFacade.refund(orderId, RefundReason.PRODUCT_DEFECT);

        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.pgCancelTransactionId()).isEqualTo(firstCancelTransactionId);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("주문 환불 실패로 결제 취소만 커밋된 뒤 재시도가 이미 취소된 결제를 관용해 복원을 완결한다")
    void retryToleratesCancelledPaymentWhenOrderRefundFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = deliveredCheckout(memberId, issuedId);

        doThrow(new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION))
                .when(orderModifier)
                .refund(eq(orderId), any());
        assertThatThrownBy(() -> orderRefundFacade.refund(orderId, RefundReason.PRODUCT_DEFECT))
                .isInstanceOf(OrderStatusException.class);
        Mockito.reset(orderModifier);

        orderRefundFacade.refund(orderId, RefundReason.PRODUCT_DEFECT);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    private UUID deliveredCheckout(UUID memberId, @org.jspecify.annotations.Nullable UUID issuedCouponId) {
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedCouponId, PaymentMethod.CARD);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
        return orderId;
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
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

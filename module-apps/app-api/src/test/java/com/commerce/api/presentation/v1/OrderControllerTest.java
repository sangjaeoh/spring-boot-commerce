package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.AddressRequest;
import com.commerce.api.presentation.v1.request.CheckoutRequest;
import com.commerce.api.presentation.v1.request.FulfillmentHoldRequest;
import com.commerce.api.presentation.v1.response.CheckoutResponse;
import com.commerce.api.presentation.v1.response.PaymentResponse;
import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.HoldReason;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderAppender;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderControllerTest extends WebIntegrationTest {

    private static final String KEY_HEADER = "Idempotency-Key";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final StockReader stockReader;

    OrderControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            OrderAppender orderAppender,
            OrderReader orderReader,
            OrderModifier orderModifier,
            StockReader stockReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.stockReader = stockReader;
    }

    @Test
    @DisplayName("쿠폰·배송비 체크아웃이 201로 주문을 결제하고 할인·결제금액·쿠폰을 반영한다")
    void checkoutAppliesCouponShippingAndReturnsPaidOrder() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 3000L, issuedId, PaymentMethod.CARD);

        String body = mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.discountAmount()).isEqualTo(Money.of(2000L));
        assertThat(order.payAmount()).isEqualTo(Money.of(21000L));
        assertThat(order.issuedCouponId()).isEqualTo(issuedId);
    }

    @Test
    @DisplayName("회원 ID가 없는 체크아웃 요청은 400 problem+json으로 거부된다")
    void checkoutRejectsInvalidRequest() throws Exception {
        String json = """
                {"shippingAddress":{"recipientName":"홍길동","zipCode":"04524",\
                "roadAddress":"서울특별시 중구 세종대로 110","phone":"010-1234-5678"},"shippingFee":0}""";

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("빈 장바구니 체크아웃은 409 API_EMPTY_CART로 거부된다")
    void checkoutRejectsEmptyCart() throws Exception {
        UUID memberId = registerMember();
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_EMPTY_CART"));
    }

    @Test
    @DisplayName("결제 완료 주문 취소는 204로 성공하고 주문을 CANCELLED로 만든다")
    void cancelSucceedsForPaidOrder() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isNoContent());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("배송 완료 주문 취소는 409 API_ORDER_NOT_CANCELLABLE로 거부된다")
    void cancelRejectsDeliveredOrder() throws Exception {
        UUID orderId = checkoutViaHttp();
        orderModifier.ship(orderId);
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("HTTP로 출고한 주문의 취소 요청은 409 API_ORDER_NOT_CANCELLABLE로 거부되고 상태를 바꾸지 않는다")
    void cancelRejectsShippedOrderViaHttp() throws Exception {
        UUID orderId = checkoutViaHttp();
        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId)).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_NOT_CANCELLABLE"));

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 취소를 재요청하면 409로 거부되고 재고를 이중 복원하지 않는다")
    void duplicateKeyedCancelIsRejectedAndDoesNotDoubleRestore() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 3);
        UUID orderId = checkout(new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD));
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header(KEY_HEADER, key))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).header(KEY_HEADER, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 체크아웃을 재요청하면 409로 거부되고 주문을 이중 생성하지 않는다")
    void duplicateKeyedCheckoutIsRejectedAndDoesNotCreateSecondOrder() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 2);
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/orders")
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders")
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        mvc.perform(get("/api/v1/orders").param("memberId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("출고·배송완료 전이가 각각 204로 성공하고 이행 축 상태·시각을 전진시킨다")
    void shipThenConfirmDeliveryProgressesFulfillment() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt").exists());

        mvc.perform(post("/api/v1/orders/{orderId}/delivery-confirmation", orderId))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").exists());
    }

    @Test
    @DisplayName("보류·해제 왕복이 각각 204로 사유를 세팅·클리어하고 해제 후 출고가 가능하다")
    void holdAndReleaseRoundTripRestoresShippability() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(post("/api/v1/orders/{orderId}/fulfillment-hold", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FulfillmentHoldRequest(HoldReason.FRAUD_REVIEW))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("ON_HOLD"))
                .andExpect(jsonPath("$.holdReason").value("FRAUD_REVIEW"));

        mvc.perform(post("/api/v1/orders/{orderId}/fulfillment-release", orderId))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("PREPARING"))
                .andExpect(jsonPath("$.holdReason").doesNotExist());

        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("미결제(PENDING) 주문 출고는 409 ORDER_NOT_PAID로 거부된다")
    void shipRejectsPendingOrder() throws Exception {
        UUID orderId = placePendingOrder();

        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
    }

    @Test
    @DisplayName("취소된 주문 출고는 409 ORDER_NOT_PAID로 거부된다")
    void shipRejectsCancelledOrder() throws Exception {
        UUID orderId = checkoutViaHttp();
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/orders/{orderId}/ship", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
    }

    @Test
    @DisplayName("출고 전 배송완료는 409 ORDER_INVALID_FULFILLMENT_TRANSITION으로 거부된다")
    void confirmDeliveryRejectsUnshippedOrder() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(post("/api/v1/orders/{orderId}/delivery-confirmation", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_FULFILLMENT_TRANSITION"));
    }

    @Test
    @DisplayName("없는 주문 출고는 404 ORDER_NOT_FOUND로 거부된다")
    void shipReturns404ForMissingOrder() throws Exception {
        mvc.perform(post("/api/v1/orders/{orderId}/ship", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("사유가 없는 보류 요청은 400 VALIDATION_FAILED로 거부된다")
    void holdRejectsMissingReason() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(post("/api/v1/orders/{orderId}/fulfillment-hold", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("주문 상세 조회는 200으로 결제 축·이행 축 상태와 결제 시각을 싣는다")
    void getOrderReturnsDetailWithPaidTimeline() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("PREPARING"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.payAmount").value(10000))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    @DisplayName("없는 주문 상세 조회는 404 ORDER_NOT_FOUND로 거부된다")
    void getOrderReturns404ForMissingOrder() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("취소된 주문 상세는 취소 시각·사유를 싣는다")
    void getOrderShowsCancellationAfterCancel() throws Exception {
        UUID orderId = checkoutViaHttp();
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellationReason").value("CUSTOMER_REQUEST"));
    }

    @Test
    @DisplayName("결제 조회는 200으로 승인 상태·수단·금액·승인 거래 ID·승인 시각을 싣는다")
    void getPaymentReturnsApprovedTransaction() throws Exception {
        UUID orderId = checkoutViaHttp();

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andExpect(jsonPath("$.pgTransactionId").exists())
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.pgCancelTransactionId").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist());
    }

    @Test
    @DisplayName("취소된 주문의 결제 조회는 CANCELLED와 승인 ID와 별개인 환불 거래 ID·취소 시각을 싣는다")
    void getPaymentShowsDistinctRefundTransactionAfterCancel() throws Exception {
        UUID orderId = checkoutViaHttp();
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isNoContent());

        String body = mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        PaymentResponse payment = objectMapper.readValue(body, PaymentResponse.class);
        assertThat(payment.pgTransactionId()).isNotNull();
        assertThat(payment.pgCancelTransactionId()).isNotNull();
        assertThat(payment.pgCancelTransactionId()).isNotEqualTo(payment.pgTransactionId());
    }

    @Test
    @DisplayName("전액 할인 0원 결제 조회는 APPROVED에 수단·승인 거래 ID 없이 승인 시각을 싣는다")
    void getPaymentOmitsGatewayFieldsForZeroAmountPayment() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 1);
        UUID couponId = couponAppender.create("100% 할인", Discount.rate(100), Money.of(10000L), validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkout(new CheckoutRequest(memberId, addressRequest(), 0L, issuedId, null));

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(0))
                .andExpect(jsonPath("$.method").doesNotExist())
                .andExpect(jsonPath("$.pgTransactionId").doesNotExist())
                .andExpect(jsonPath("$.approvedAt").exists());
    }

    @Test
    @DisplayName("결제가 없는 주문의 결제 조회는 404 PAYMENT_NOT_FOUND로 거부된다")
    void getPaymentReturns404ForOrderWithoutPayment() throws Exception {
        UUID orderId = placePendingOrder();

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("회원 주문 목록 조회는 200으로 최신순 주문을 싣는다")
    void getOrdersReturnsMemberOrdersNewestFirst() throws Exception {
        UUID memberId = registerMember();
        UUID first = checkoutForMember(memberId);
        UUID second = checkoutForMember(memberId);

        mvc.perform(get("/api/v1/orders").param("memberId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()));
    }

    @Test
    @DisplayName("여러 라인 주문 목록은 주문 중복 없이 라인을 모두 싣는다")
    void getOrdersFetchesMultiLineOrderWithoutDuplication() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 1);
        cartAppender.addItem(memberId, seedProduct(50), 2);
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/orders").param("memberId", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lines.length()").value(2));
    }

    private UUID checkoutViaHttp() throws Exception {
        return checkoutForMember(registerMember());
    }

    private UUID checkoutForMember(UUID memberId) throws Exception {
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);

        String body = mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
    }

    private UUID checkout(CheckoutRequest request) throws Exception {
        String body = mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
    }

    private UUID placePendingOrder() {
        OrderLineSnapshot line =
                new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "상품", null, Money.of(10000L), 1);
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(registerMember(), List.of(line), address, Money.ZERO, Money.ZERO, null);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static AddressRequest addressRequest() {
        return new AddressRequest("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

package com.commerce.api.web.v1.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.order.request.OrderShipRequest;
import com.commerce.api.web.v1.order.request.AddressRequest;
import com.commerce.api.web.v1.order.request.CheckoutPreviewRequest;
import com.commerce.api.web.v1.order.request.CheckoutRequest;
import com.commerce.api.web.v1.order.request.DirectOrderItemRequest;
import com.commerce.api.web.v1.order.request.DirectOrderRequest;
import com.commerce.api.web.v1.order.request.OrderReturnRequest;
import com.commerce.api.web.v1.order.response.CheckoutResponse;
import com.commerce.api.web.v1.order.response.DirectOrderResponse;
import com.commerce.api.web.v1.payment.response.PaymentResponse;
import com.commerce.cart.info.CartInfo;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartReader;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.entity.RefundReason;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    private final CartReader cartReader;
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
            CartReader cartReader,
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
        this.cartReader = cartReader;
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
    @DisplayName("쿠폰·배송비 체크아웃이 201로 토큰 주체의 주문을 결제하고 할인·결제금액·쿠폰을 반영한다")
    void checkoutAppliesCouponShippingAndReturnsPaidOrder() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 3000L, issuedId, PaymentMethod.CARD);

        String body = mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.discountAmount()).isEqualTo(Money.of(2000L));
        assertThat(order.payAmount()).isEqualTo(Money.of(21000L));
        assertThat(order.issuedCouponId()).isEqualTo(issuedId);
    }

    @Test
    @DisplayName("미리보기가 200으로 금액 4종을 반환하고 같은 조건 체크아웃 결제금액과 일치한다")
    void previewMatchesCheckoutPayAmount() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(null, 3000L, issuedId);

        mvc.perform(post("/api/v1/orders/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(20000))
                .andExpect(jsonPath("$.discountAmount").value(2000))
                .andExpect(jsonPath("$.shippingFee").value(3000))
                .andExpect(jsonPath("$.payAmount").value(21000));

        // 미리보기가 부작용을 남기지 않아 같은 장바구니·쿠폰으로 체크아웃이 그대로 성공한다.
        UUID orderId =
                checkout(memberId, new CheckoutRequest(null, addressRequest(), 3000L, issuedId, PaymentMethod.CARD));
        assertThat(orderReader.getOrder(orderId, memberId).payAmount()).isEqualTo(Money.of(21000L));
    }

    @Test
    @DisplayName("선택 체크아웃이 201로 선택 라인만 주문하고 미선택 라인은 장바구니에 남긴다")
    void partialCheckoutOrdersSelectionOnly() throws Exception {
        UUID memberId = registerMember();
        UUID keptVariantId = seedProduct(50);
        UUID orderedVariantId = seedProduct(50);
        cartAppender.addItem(memberId, keptVariantId, 1);
        cartAppender.addItem(memberId, orderedVariantId, 2);
        CheckoutRequest request =
                new CheckoutRequest(List.of(orderedVariantId), addressRequest(), 3000L, null, PaymentMethod.CARD);

        String body = mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.payAmount()).isEqualTo(Money.of(23000L));
        assertThat(stockReader.getByVariantId(orderedVariantId).quantity()).isEqualTo(48);
        assertThat(stockReader.getByVariantId(keptVariantId).quantity()).isEqualTo(50);
        CartInfo cart = cartReader.getCart(memberId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).variantId()).isEqualTo(keptVariantId);
    }

    @Test
    @DisplayName("장바구니에 없는 변형 선택 체크아웃은 409 API_CART_ITEM_NOT_FOUND로 거부된다")
    void partialCheckoutRejectsUnknownSelection() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 1);
        CheckoutRequest request =
                new CheckoutRequest(List.of(UUID.randomUUID()), addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_CART_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("선택 미리보기가 선택 라인 기준으로 금액을 계산한다")
    void partialPreviewPricesSelectionOnly() throws Exception {
        UUID memberId = registerMember();
        UUID keptVariantId = seedProduct(50);
        UUID selectedVariantId = seedProduct(50);
        cartAppender.addItem(memberId, keptVariantId, 1);
        cartAppender.addItem(memberId, selectedVariantId, 2);
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(List.of(selectedVariantId), 3000L, null);

        mvc.perform(post("/api/v1/orders/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(20000))
                .andExpect(jsonPath("$.payAmount").value(23000));
    }

    @Test
    @DisplayName("바로구매가 201로 주문을 결제하고 장바구니를 건드리지 않는다")
    void directOrderPaysWithoutTouchingCart() throws Exception {
        UUID memberId = registerMember();
        UUID cartVariantId = seedProduct(50);
        cartAppender.addItem(memberId, cartVariantId, 1);
        UUID directVariantId = seedProduct(50);
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(directVariantId, 2)),
                addressRequest(),
                3000L,
                null,
                PaymentMethod.CARD);

        String body = mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readValue(body, DirectOrderResponse.class).orderId());
        OrderInfo order = orderReader.getOrder(orderId, memberId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.payAmount()).isEqualTo(Money.of(23000L));
        assertThat(stockReader.getByVariantId(directVariantId).quantity()).isEqualTo(48);
        CartInfo cart = cartReader.getCart(memberId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).variantId()).isEqualTo(cartVariantId);
    }

    @Test
    @DisplayName("존재하지 않는 변형 바로구매는 409 API_NOT_ORDERABLE로 거부된다")
    void directOrderRejectsUnknownVariant() throws Exception {
        UUID memberId = registerMember();
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(UUID.randomUUID(), 1)),
                addressRequest(),
                0L,
                null,
                PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_NOT_ORDERABLE"));
    }

    @Test
    @DisplayName("재고 부족 바로구매는 409 API_INSUFFICIENT_STOCK로 거부된다")
    void directOrderRejectsInsufficientStock() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(1);
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(variantId, 2)), addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("결제 거절 바로구매는 402 API_PAYMENT_DECLINED로 거부되고 재고를 원복한다")
    void directOrderCompensatesOnPaymentDecline() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        // 결제 예정액 20999 — 끝 세 자리 999는 fake PG의 거절 트리거다.
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(variantId, 2)), addressRequest(), 999L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("API_PAYMENT_DECLINED"));

        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("미인증 바로구매는 401 UNAUTHENTICATED로 거부된다")
    void directOrderRejectsUnauthenticated() throws Exception {
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(UUID.randomUUID(), 1)),
                addressRequest(),
                0L,
                null,
                PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders/direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("빈 장바구니 미리보기는 409 API_EMPTY_CART로 거부된다")
    void previewRejectsEmptyCart() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(post("/api/v1/orders/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutPreviewRequest(null, 0L, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_EMPTY_CART"));
    }

    @Test
    @DisplayName("최소 주문 금액 미달 쿠폰 미리보기는 409 API_COUPON_NOT_APPLICABLE로 거부된다")
    void previewRejectsInapplicableCoupon() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 2);
        UUID couponId = couponAppender.create("고액 전용", Discount.rate(10), Money.of(100000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);

        mvc.perform(post("/api/v1/orders/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutPreviewRequest(null, 3000L, issuedId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_COUPON_NOT_APPLICABLE"));
    }

    @Test
    @DisplayName("미인증 미리보기는 401 UNAUTHENTICATED로 거부된다")
    void previewRejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/orders/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CheckoutPreviewRequest(null, 0L, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("미인증 체크아웃은 401 UNAUTHENTICATED로 거부된다")
    void checkoutRejectsUnauthenticated() throws Exception {
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("배송지가 없는 체크아웃 요청은 400 problem+json으로 거부된다")
    void checkoutRejectsInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingFee\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("빈 장바구니 체크아웃은 409 API_EMPTY_CART로 거부된다")
    void checkoutRejectsEmptyCart() throws Exception {
        UUID memberId = registerMember();
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);

        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_EMPTY_CART"));
    }

    @Test
    @DisplayName("결제 완료 주문 취소는 204로 성공하고 주문을 CANCELLED로 만든다")
    void cancelSucceedsForPaidOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        assertThat(orderReader.getOrder(orderId, memberId).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("타인 주문 취소는 404 ORDER_NOT_FOUND로 거부되고 주문 상태를 바꾸지 않는다")
    void cancelRejectsOtherMembersOrderAsNotFound() throws Exception {
        UUID ownerId = registerMember();
        UUID orderId = checkoutForMember(ownerId);
        UUID intruderId = registerMember();

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(orderReader.getOrder(orderId, ownerId).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("배송 완료 주문 취소는 409 API_ORDER_NOT_CANCELLABLE로 거부된다")
    void cancelRejectsDeliveredOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("HTTP로 출고한 주문의 취소 요청은 409 API_ORDER_NOT_CANCELLABLE로 거부되고 상태를 바꾸지 않는다")
    void cancelRejectsShippedOrderViaHttp() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_NOT_CANCELLABLE"));

        OrderInfo order = orderReader.getOrder(orderId, memberId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
    }

    @Test
    @DisplayName("배송 완료 주문 반품 요청은 204로 성공하고 상세에 요청 상태·사유·시각을 싣는다. 중복 요청은 409다")
    void returnRequestSucceedsForDeliveredOrderAndRejectsDuplicate() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/orders/{orderId}/return-request", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderReturnRequest(RefundReason.PRODUCT_DEFECT))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.returnStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.returnReason").value("PRODUCT_DEFECT"))
                .andExpect(jsonPath("$.returnRequestedAt").exists());

        mvc.perform(post("/api/v1/orders/{orderId}/return-request", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderReturnRequest(RefundReason.PRODUCT_DEFECT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_RETURN_ALREADY_REQUESTED"));
    }

    @Test
    @DisplayName("배송 완료 전 주문 반품 요청은 409 ORDER_RETURN_NOT_ALLOWED로 거부된다")
    void returnRequestRejectsUndeliveredOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/orders/{orderId}/return-request", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderReturnRequest(RefundReason.CHANGE_OF_MIND))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_RETURN_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("타인 주문 반품 요청은 404 ORDER_NOT_FOUND로 거부되고 상태를 바꾸지 않는다")
    void returnRequestRejectsOtherMembersOrder() throws Exception {
        UUID ownerId = registerMember();
        UUID orderId = checkoutForMember(ownerId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
        UUID intruderId = registerMember();

        mvc.perform(post("/api/v1/orders/{orderId}/return-request", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderReturnRequest(RefundReason.CHANGE_OF_MIND))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(orderReader.getOrder(orderId, ownerId).returnStatus()).isNull();
    }

    @Test
    @DisplayName("사유가 없는 반품 요청은 400 VALIDATION_FAILED로 거부된다")
    void returnRequestRejectsMissingReason() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/orders/{orderId}/return-request", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 취소를 재요청하면 409로 거부되고 재고를 이중 복원하지 않는다")
    void duplicateKeyedCancelIsRejectedAndDoesNotDoubleRestore() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 3);
        UUID orderId = checkout(memberId, new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD));
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        assertThat(orderReader.getOrder(orderId, memberId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 체크아웃을 재요청하면 409로 거부되고 주문을 이중 생성하지 않는다")
    void duplicateKeyedCheckoutIsRejectedAndDoesNotCreateSecondOrder() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 2);
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1));
    }

    @Test
    @DisplayName("서로 다른 회원이 같은 Idempotency-Key로 체크아웃해도 각자 주문이 생성된다")
    void sameKeyDifferentMembersEachCreateOrder() throws Exception {
        String key = UUID.randomUUID().toString();
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);
        UUID firstMemberId = registerMember();
        cartAppender.addItem(firstMemberId, seedProduct(50), 1);
        UUID secondMemberId = registerMember();
        cartAppender.addItem(secondMemberId, seedProduct(50), 1);

        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstMemberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondMemberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("같은 회원이 같은 Idempotency-Key로 동시에 체크아웃하면 1건만 생성되고 나머지는 409로 거부된다")
    void concurrentSameKeyCheckoutsCreateSingleOrder() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 1);
        String key = UUID.randomUUID().toString();
        String authorization = bearer(memberId);
        String body = objectMapper.writeValueAsString(
                new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD));

        int threadCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .header(KEY_HEADER, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        ready.await();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(statuses.stream().sorted().toList()).isEqualTo(List.of(201, 409, 409, 409));
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1));
    }

    @Test
    @DisplayName("Idempotency-Key 없는 체크아웃 재요청은 멱등 보장 없이 각각 주문을 생성한다")
    void checkoutWithoutKeyCreatesOrderEachTime() throws Exception {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);

        cartAppender.addItem(memberId, variantId, 1);
        checkout(memberId, request);
        cartAppender.addItem(memberId, variantId, 1);
        checkout(memberId, request);

        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 바로구매를 재요청하면 409로 거부되고 주문을 이중 생성하지 않는다")
    void duplicateKeyedDirectOrderIsRejectedAndDoesNotCreateSecondOrder() throws Exception {
        UUID memberId = registerMember();
        DirectOrderRequest request = new DirectOrderRequest(
                List.of(new DirectOrderItemRequest(seedProduct(50), 1)),
                addressRequest(),
                0L,
                null,
                PaymentMethod.CARD);
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1));
    }

    @Test
    @DisplayName("주문 상세 조회는 200으로 결제 축·이행 축 상태와 결제 시각을 싣는다")
    void getOrderReturnsDetailWithPaidTimeline() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("PREPARING"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.carrier").doesNotExist())
                .andExpect(jsonPath("$.trackingNumber").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.payAmount").value(10000))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    @DisplayName("미인증 주문 상세 조회는 401 UNAUTHENTICATED로 거부된다")
    void getOrderRejectsUnauthenticated() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("타인 주문 상세 조회는 404 ORDER_NOT_FOUND로 미존재 취급된다")
    void getOrderTreatsOtherMembersOrderAsNotFound() throws Exception {
        UUID orderId = checkoutForMember(registerMember());
        UUID intruderId = registerMember();

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer(intruderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 주문 상세 조회는 404 ORDER_NOT_FOUND로 거부된다")
    void getOrderReturns404ForMissingOrder() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("취소된 주문 상세는 취소 시각·사유를 싣는다")
    void getOrderShowsCancellationAfterCancel() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellationReason").value("CUSTOMER_REQUEST"));
    }

    @Test
    @DisplayName("결제 조회는 200으로 승인 상태·수단·금액·승인 거래 ID·승인 시각을 싣는다")
    void getPaymentReturnsApprovedTransaction() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
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
    @DisplayName("타인 주문 결제 조회는 404 ORDER_NOT_FOUND로 미존재 취급된다")
    void getPaymentTreatsOtherMembersOrderAsNotFound() throws Exception {
        UUID orderId = checkoutForMember(registerMember());
        UUID intruderId = registerMember();

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("취소된 주문의 결제 조회는 CANCELLED와 승인 ID와 별개인 환불 거래 ID·취소 시각을 싣는다")
    void getPaymentShowsDistinctRefundTransactionAfterCancel() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        mvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        String body = mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
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
        UUID couponId = couponAppender.create("100% 할인", Discount.rate(100), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkout(memberId, new CheckoutRequest(null, addressRequest(), 0L, issuedId, null));

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
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
        UUID memberId = registerMember();
        UUID orderId = placePendingOrder(memberId);

        mvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("주문 목록 조회는 200으로 토큰 주체의 주문만 최신순 페이지 정보와 함께 싣는다")
    void getOrdersReturnsMemberOrdersNewestFirst() throws Exception {
        UUID memberId = registerMember();
        UUID first = checkoutForMember(memberId);
        UUID second = checkoutForMember(memberId);
        checkoutForMember(registerMember());

        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].id").value(second.toString()))
                .andExpect(jsonPath("$.orders[1].id").value(first.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    @DisplayName("주문 목록 페이지 경계 — 마지막 페이지는 나머지만 싣고 범위 밖 페이지는 빈 목록에 총계를 유지한다")
    void getOrdersKeepsTotalsAcrossPageBoundaries() throws Exception {
        UUID memberId = registerMember();
        UUID first = checkoutForMember(memberId);
        UUID second = checkoutForMember(memberId);
        UUID third = checkoutForMember(memberId);

        mvc.perform(get("/api/v1/orders")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].id").value(third.toString()))
                .andExpect(jsonPath("$.orders[1].id").value(second.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/v1/orders")
                        .param("page", "2")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].id").value(first.toString()))
                .andExpect(jsonPath("$.page.number").value(2));

        mvc.perform(get("/api/v1/orders")
                        .param("page", "3")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    @DisplayName("1 미만 page·size의 주문 목록 조회는 400 VALIDATION_FAILED로 거부된다")
    void getOrdersRejectsInvalidPageParams() throws Exception {
        String token = bearer(registerMember());

        mvc.perform(get("/api/v1/orders").param("page", "0").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        mvc.perform(get("/api/v1/orders").param("size", "0").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("여러 라인 주문 목록은 주문 중복 없이 라인을 모두 싣는다")
    void getOrdersFetchesMultiLineOrderWithoutDuplication() throws Exception {
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, seedProduct(50), 1);
        cartAppender.addItem(memberId, seedProduct(50), 2);
        CheckoutRequest request = new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD);
        mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].lines.length()").value(2));
    }

    private String shipRequestBody() {
        return objectMapper.writeValueAsString(new OrderShipRequest("CJ대한통운", "688900123456"));
    }

    private UUID checkoutForMember(UUID memberId) throws Exception {
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        return checkout(memberId, new CheckoutRequest(null, addressRequest(), 0L, null, PaymentMethod.CARD));
    }

    private UUID checkout(UUID memberId, CheckoutRequest request) throws Exception {
        String body = mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, CheckoutResponse.class).orderId());
    }

    private UUID placePendingOrder(UUID memberId) {
        OrderLineSnapshot line =
                new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "상품", null, Money.of(10000L), 1);
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(memberId, List.of(line), address, Money.ZERO, Money.ZERO, null);
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

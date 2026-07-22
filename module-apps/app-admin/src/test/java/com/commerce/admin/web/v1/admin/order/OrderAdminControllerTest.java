package com.commerce.admin.web.v1.admin.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.facade.ProductRegistrationFacade;
import com.commerce.admin.web.v1.WebIntegrationTest;
import com.commerce.admin.web.v1.admin.order.request.FulfillmentHoldRequest;
import com.commerce.admin.web.v1.admin.order.request.OrderRefundRequest;
import com.commerce.admin.web.v1.admin.order.request.OrderShipRequest;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.order.application.info.OrderInfo;
import com.commerce.order.application.provided.OrderAppender;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.CancellationReason;
import com.commerce.order.domain.FulfillmentStatus;
import com.commerce.order.domain.HoldReason;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.domain.RefundReason;
import com.commerce.order.domain.ReturnStatus;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.domain.PaymentStatus;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final OrderModifier orderModifier;

    OrderAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            OrderAppender orderAppender,
            OrderReader orderReader,
            PaymentReader paymentReader,
            OrderModifier orderModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.orderModifier = orderModifier;
    }

    @Test
    @DisplayName("출고·배송완료 전이가 각각 204로 성공하고 이행 축 상태·시각·운송장 기록을 전진시킨다")
    void shipThenConfirmDeliveryProgressesFulfillment() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isNoContent());
        OrderInfo shipped = orderReader.getOrder(orderId, memberId);
        assertThat(shipped.status()).isEqualTo(OrderStatus.PAID);
        assertThat(shipped.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(shipped.shippedAt()).isNotNull();
        assertThat(shipped.carrier()).isEqualTo("CJ대한통운");
        assertThat(shipped.trackingNumber()).isEqualTo("688900123456");

        mvc.perform(post("/api/v1/admin/orders/{orderId}/delivery-confirmation", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        OrderInfo delivered = orderReader.getOrder(orderId, memberId);
        assertThat(delivered.status()).isEqualTo(OrderStatus.PAID);
        assertThat(delivered.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(delivered.deliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("보류·해제 왕복이 각각 204로 사유를 세팅·클리어하고 해제 후 출고가 가능하다")
    void holdAndReleaseRoundTripRestoresShippability() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/fulfillment-hold", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FulfillmentHoldRequest(HoldReason.FRAUD_REVIEW))))
                .andExpect(status().isNoContent());
        OrderInfo held = orderReader.getOrder(orderId, memberId);
        assertThat(held.fulfillmentStatus()).isEqualTo(FulfillmentStatus.ON_HOLD);
        assertThat(held.holdReason()).isEqualTo(HoldReason.FRAUD_REVIEW);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/fulfillment-release", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        OrderInfo released = orderReader.getOrder(orderId, memberId);
        assertThat(released.fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(released.holdReason()).isNull();

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("미결제(PENDING) 주문 출고는 409 ORDER_NOT_PAID로 거부된다")
    void shipRejectsPendingOrder() throws Exception {
        UUID orderId = placePendingOrder(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
    }

    @Test
    @DisplayName("취소된 주문 출고는 409 ORDER_NOT_PAID로 거부된다")
    void shipRejectsCancelledOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        // 구매자 취소 표면은 app-api 소유라 취소 상태를 도메인 서비스로 재현한다.
        orderModifier.cancel(orderId, CancellationReason.CUSTOMER_REQUEST);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
    }

    @Test
    @DisplayName("출고 전 배송완료는 409 ORDER_INVALID_FULFILLMENT_TRANSITION으로 거부된다")
    void confirmDeliveryRejectsUnshippedOrder() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/delivery-confirmation", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_FULFILLMENT_TRANSITION"));
    }

    @Test
    @DisplayName("없는 주문 출고는 404 ORDER_NOT_FOUND로 거부된다")
    void shipReturns404ForMissingOrder() throws Exception {
        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("택배사·운송장 번호가 없거나 공백인 출고 요청은 400 VALIDATION_FAILED로 거부된다")
    void shipRejectsMissingTrackingInfo() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderShipRequest(" ", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("구매자 토큰의 본인 주문 출고는 403 FORBIDDEN으로 거부되고 이행 상태를 바꾸지 않는다")
    void shipRejectsBuyerToken() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/ship", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(orderReader.getOrder(orderId, memberId).fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
    }

    @Test
    @DisplayName("사유가 없는 보류 요청은 400 VALIDATION_FAILED로 거부된다")
    void holdRejectsMissingReason() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/fulfillment-hold", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("배송 완료 주문의 관리자 환불은 204로 주문을 REFUNDED로 전이하고 환불 거래를 남긴다")
    void adminRefundSucceedsForDeliveredOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/refund", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRefundRequest(RefundReason.PRODUCT_DEFECT))))
                .andExpect(status().isNoContent());

        OrderInfo refunded = orderReader.getOrder(orderId, memberId);
        assertThat(refunded.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(refunded.refundedAt()).isNotNull();
        assertThat(refunded.refundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.pgCancelTransactionId()).isNotNull();
    }

    @Test
    @DisplayName("구매자 토큰의 본인 주문 환불은 403 FORBIDDEN으로 거부되고 상태를 바꾸지 않는다")
    void refundRejectsBuyerToken() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/refund", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRefundRequest(RefundReason.PRODUCT_DEFECT))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(orderReader.getOrder(orderId, memberId).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("배송 완료 전 주문의 관리자 환불은 409 API_ORDER_NOT_REFUNDABLE로 거부된다")
    void refundRejectsUndeliveredOrderViaHttp() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/refund", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRefundRequest(RefundReason.PRODUCT_DEFECT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_NOT_REFUNDABLE"));
    }

    @Test
    @DisplayName("사유가 없는 환불 요청은 400 VALIDATION_FAILED로 거부된다")
    void refundRejectsMissingReason() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(post("/api/v1/admin/orders/{orderId}/refund", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("반품 요청 승인은 204로 환불을 완결하고 반품을 COMPLETED로 닫는다")
    void returnApprovalRefundsRequestedOrder() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
        orderModifier.requestReturn(orderId, memberId, RefundReason.PRODUCT_DEFECT);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/return-approval", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        OrderInfo refunded = orderReader.getOrder(orderId, memberId);
        assertThat(refunded.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.returnStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(refunded.refundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(refunded.refundedAt()).isNotNull();
    }

    @Test
    @DisplayName("반품 요청 거절은 204로 주문을 PAID·DELIVERED로 남기고 REJECTED를 기록한다")
    void returnRejectionKeepsOrderPaid() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
        orderModifier.requestReturn(orderId, memberId, RefundReason.CHANGE_OF_MIND);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/return-rejection", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        OrderInfo rejected = orderReader.getOrder(orderId, memberId);
        assertThat(rejected.status()).isEqualTo(OrderStatus.PAID);
        assertThat(rejected.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(rejected.returnStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(rejected.refundedAt()).isNull();
    }

    @Test
    @DisplayName("반품 요청 없는 주문 승인은 409 API_ORDER_RETURN_NOT_REQUESTED로 거부된다")
    void returnApprovalRejectsWithoutRequest() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/return-approval", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_ORDER_RETURN_NOT_REQUESTED"));

        assertThat(orderReader.getOrder(orderId, memberId).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("구매자 토큰의 반품 승인·거절은 403 FORBIDDEN으로 거부된다")
    void returnDecisionRejectsBuyerToken() throws Exception {
        UUID memberId = registerMember();
        UUID orderId = checkoutForMember(memberId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
        orderModifier.requestReturn(orderId, memberId, RefundReason.CHANGE_OF_MIND);

        mvc.perform(post("/api/v1/admin/orders/{orderId}/return-approval", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/admin/orders/{orderId}/return-rejection", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(orderReader.getOrder(orderId, memberId).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("관리자 주문 목록은 200으로 결제완료·준비중 주문을 최신순 페이지로 싣고 이행 축 상태를 따른다")
    void adminOrderListReturnsPaidPreparingOrdersAndFollowsFulfillment() throws Exception {
        UUID orderId = checkoutForMember(registerMember());

        mvc.perform(get("/api/v1/admin/orders")
                        .param("status", "PAID")
                        .param("fulfillmentStatus", "PREPARING")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.orders[0].status").value("PAID"))
                .andExpect(jsonPath("$.orders[0].fulfillmentStatus").value("PREPARING"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());

        orderModifier.ship(orderId, "CJ대한통운", "688900123456");

        mvc.perform(get("/api/v1/admin/orders")
                        .param("status", "PAID")
                        .param("fulfillmentStatus", "SHIPPED")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.orders[0].fulfillmentStatus").value("SHIPPED"));
    }

    @Test
    @DisplayName("관리자 주문 목록은 결제 축 상태로 미결제(PENDING) 주문을 구분해 싣는다")
    void adminOrderListFiltersByPaymentStatus() throws Exception {
        UUID pendingOrderId = placePendingOrder(registerMember());

        mvc.perform(get("/api/v1/admin/orders")
                        .param("status", "PENDING")
                        .param("fulfillmentStatus", "NOT_STARTED")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].id").value(pendingOrderId.toString()))
                .andExpect(jsonPath("$.orders[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("구매자 토큰의 관리자 주문 목록 조회는 403 FORBIDDEN으로 거부된다")
    void adminOrderListRejectsBuyerToken() throws Exception {
        mvc.perform(get("/api/v1/admin/orders")
                        .param("status", "PAID")
                        .param("fulfillmentStatus", "PREPARING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 관리자 주문 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void adminOrderListRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").param("status", "PAID").param("fulfillmentStatus", "PREPARING"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String shipRequestBody() {
        return objectMapper.writeValueAsString(new OrderShipRequest("CJ대한통운", "688900123456"));
    }

    private UUID checkoutForMember(UUID memberId) {
        UUID variantId = seedProduct(50);
        return placePaidOrder(memberId, variantId, 1);
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
}

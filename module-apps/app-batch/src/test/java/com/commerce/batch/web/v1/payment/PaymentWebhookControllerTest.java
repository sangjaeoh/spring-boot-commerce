package com.commerce.batch.web.v1.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.batch.BatchIntegrationTest;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.order.application.provided.OrderAppender;
import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.order.domain.OrderStatus;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentAppender;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.application.required.PaymentGateway;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.payment.domain.PaymentStatus;
import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockModifier;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PG 웹훅 수신의 서명 검증·확정·중복 전달 무해를 검증하는 테스트다.
 *
 * <p>fake PG는 웹훅을 발신하지 않으므로 실 PG가 보낼 요청을 MockMvc로 직접 재현한다. 확정 대상 결제는
 * 유예({@code stale-after})가 지나야 하므로 생성 시각을 SQL로 과거로 되돌려 재현한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@AutoConfigureMockMvc
class PaymentWebhookControllerTest extends BatchIntegrationTest {

    private static final String WEBHOOK_PATH = "/api/v1/payments/webhook";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final MockMvc mvc;
    private final JdbcTemplate jdbcTemplate;
    private final MemberAppender memberAppender;
    private final ProductVariantReader variantReader;
    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final StockModifier stockModifier;
    private final PaymentAppender paymentAppender;
    private final PaymentReader paymentReader;
    private final PaymentGateway paymentGateway;

    PaymentWebhookControllerTest(
            MockMvc mvc,
            JdbcTemplate jdbcTemplate,
            MemberAppender memberAppender,
            ProductVariantReader variantReader,
            OrderAppender orderAppender,
            OrderReader orderReader,
            StockModifier stockModifier,
            PaymentAppender paymentAppender,
            PaymentReader paymentReader,
            PaymentGateway paymentGateway) {
        this.mvc = mvc;
        this.jdbcTemplate = jdbcTemplate;
        this.memberAppender = memberAppender;
        this.variantReader = variantReader;
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.stockModifier = stockModifier;
        this.paymentAppender = paymentAppender;
        this.paymentReader = paymentReader;
        this.paymentGateway = paymentGateway;
    }

    @Test
    @DisplayName("유효 서명 통지는 응답 유실 결제를 승인 확정하고 중복 전달은 상태를 바꾸지 않는다")
    void validNotificationConfirmsPaymentAndDuplicateIsHarmless() throws Exception {
        LostResponsePayment state = lostResponseApprovedPayment();
        String body = "{\"paymentId\":\"" + state.paymentId() + "\"}";

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body)))
                .andExpect(status().isOk());

        PaymentInfo confirmed = paymentReader.getPayment(state.paymentId());
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(confirmed.pgTransactionId()).isNotNull();
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PAID);

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body)))
                .andExpect(status().isOk());

        assertThat(paymentReader.getPayment(state.paymentId()).pgTransactionId())
                .isEqualTo(confirmed.pgTransactionId());
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("유예가 지나지 않은 결제의 통지는 손대지 않는다 — 동기 체크아웃 경합 차단")
    void notificationForYoungPaymentIsIgnored() throws Exception {
        LostResponsePayment state = lostResponseApprovedPayment(false);
        String body = "{\"paymentId\":\"" + state.paymentId() + "\"}";

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body)))
                .andExpect(status().isOk());

        assertThat(paymentReader.getPayment(state.paymentId()).status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("서명 불일치 통지는 401로 거부하고 결제를 손대지 않는다")
    void mismatchedSignatureIsRejected() throws Exception {
        LostResponsePayment state = lostResponseApprovedPayment();
        String body = "{\"paymentId\":\"" + state.paymentId() + "\"}";

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body + "tampered")))
                .andExpect(status().isUnauthorized());

        assertThat(paymentReader.getPayment(state.paymentId()).status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(orderReader.getOrder(state.orderId()).status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("서명 없는 통지는 401로 거부한다")
    void missingSignatureIsRejected() throws Exception {
        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효 서명이라도 해석할 수 없는 페이로드는 400으로 거부한다")
    void malformedPayloadIsRejected() throws Exception {
        String body = "{\"paymentId\":\"not-a-uuid\"}";

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효 서명이라도 모르는 결제의 통지는 404다")
    void unknownPaymentIsNotFound() throws Exception {
        String body = "{\"paymentId\":\"" + UUID.randomUUID() + "\"}";

        mvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(SIGNATURE_HEADER, sign(body)))
                .andExpect(status().isNotFound());
    }

    private LostResponsePayment lostResponseApprovedPayment() {
        return lostResponseApprovedPayment(true);
    }

    /** 주문 PENDING·재고 차감·결제 REQUESTED에 PG측 승인 거래만 남은 응답 유실 상태를 재현한다. */
    private LostResponsePayment lostResponseApprovedPayment(boolean agedPastStaleAfter) {
        UUID memberId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        UUID productId = seedOnSaleProduct(Money.of(10000L), 50);
        ProductVariantInfo variant = variantReader.getByProductId(productId).get(0);
        OrderLineSnapshot snapshot =
                new OrderLineSnapshot(variant.id(), productId, "상품", variant.optionLabel(), variant.price(), 2);
        UUID orderId = orderAppender.place(memberId, List.of(snapshot), address(), Money.ZERO, Money.ZERO, null);
        stockModifier.deduct(variant.id(), 2);
        UUID paymentId = paymentAppender.request(orderId, Money.of(20000L), PaymentMethod.CARD);
        paymentGateway.approve(paymentId, Money.of(20000L), PaymentMethod.CARD);
        if (agedPastStaleAfter) {
            jdbcTemplate.update(
                    "UPDATE payment.payment SET created_at = created_at - INTERVAL '1 hour' WHERE id = ?", paymentId);
        }
        return new LostResponsePayment(orderId, paymentId);
    }

    private record LostResponsePayment(UUID orderId, UUID paymentId) {}

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

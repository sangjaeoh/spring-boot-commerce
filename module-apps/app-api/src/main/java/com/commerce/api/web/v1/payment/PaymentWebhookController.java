package com.commerce.api.web.v1.payment;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.api.facade.PaymentConfirmationFacade;
import com.commerce.api.web.v1.payment.request.PaymentWebhookRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * PG 결제 확정 통지(웹훅) 수신 엔드포인트다.
 *
 * <p>발신자가 회원이 아니라 PG 시스템이므로 토큰 인증(공개·본인·관리자 분류)에 속하지 않는다.
 */
@Tag(name = "결제 웹훅", description = "PG 결제 확정 통지 수신")
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class PaymentWebhookController {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final ObjectMapper objectMapper;
    private final SecretKeySpec secretKey;

    public PaymentWebhookController(
            PaymentConfirmationFacade paymentConfirmationFacade,
            ObjectMapper objectMapper,
            @Value("${payment.webhook.secret}") String secret) {
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.objectMapper = objectMapper;
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Operation(summary = "결제 웹훅 수신", description = "통지가 지목한 결제를 PG 상태 조회로 확정한다. 서명이 유효하지 않으면 거부한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "처리됨"),
        @ApiResponse(
                responseCode = "400",
                description = "웹훅 페이로드 해석 불가",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "웹훅 서명 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "지목한 결제 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping
    public void receive(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) @Nullable String signature,
            @RequestBody String rawBody) {
        requireValidSignature(rawBody, signature);
        paymentConfirmationFacade.confirm(parse(rawBody).paymentId());
    }

    /** 본문 서명이 공유 시크릿으로 만든 서명과 일치하는지 확인한다. */
    private void requireValidSignature(String rawBody, @Nullable String signature) {
        if (signature == null || !MessageDigest.isEqual(sign(rawBody), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ApiErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    /** 본문의 HMAC-SHA256 서명을 hex 문자열 바이트로 만든다. */
    private byte[] sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of()
                    .formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256을 초기화할 수 없다", e);
        }
    }

    /** 웹훅 페이로드를 {@link PaymentWebhookRequest}로 해석한다. */
    private PaymentWebhookRequest parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentWebhookRequest.class);
        } catch (JacksonException e) {
            throw new ApiException(ApiErrorCode.WEBHOOK_PAYLOAD_INVALID);
        }
    }
}

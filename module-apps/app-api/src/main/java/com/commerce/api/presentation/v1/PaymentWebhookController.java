package com.commerce.api.presentation.v1;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.api.facade.PaymentConfirmationFacade;
import com.commerce.api.presentation.v1.request.PaymentWebhookRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>발신자가 회원이 아니라 PG 시스템이므로 토큰 인증(공개·본인·관리자 분류)에 속하지 않는다. 대신 PG와
 * 공유한 시크릿으로 요청 본문의 HMAC-SHA256 서명({@code X-Webhook-Signature}, hex)을 상수 시간 비교로
 * 검증하고, 불일치·부재는 401로 거부한다. 통지는 결제를 지목하는 트리거일 뿐 결과 확정은 파사드가 PG 상태
 * 조회로 하므로, 서명을 통과한 페이로드도 상태를 직접 쓰지 못한다. 같은 통지의 중복 전달은 무해하다(이미
 * 종결된 결제는 파사드가 무시).
 */
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

    /** 통지가 지목한 결제를 PG 상태 조회로 확정한다. 서명이 유효하지 않으면 거부한다. */
    @PostMapping
    public void receive(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) @Nullable String signature,
            @RequestBody String rawBody) {
        requireValidSignature(rawBody, signature);
        paymentConfirmationFacade.confirm(parse(rawBody).paymentId());
    }

    private void requireValidSignature(String rawBody, @Nullable String signature) {
        if (signature == null || !MessageDigest.isEqual(sign(rawBody), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ApiErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }

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

    private PaymentWebhookRequest parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaymentWebhookRequest.class);
        } catch (JacksonException e) {
            throw new ApiException(ApiErrorCode.WEBHOOK_PAYLOAD_INVALID);
        }
    }
}

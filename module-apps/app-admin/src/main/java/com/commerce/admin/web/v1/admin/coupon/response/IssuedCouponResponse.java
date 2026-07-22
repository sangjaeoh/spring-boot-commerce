package com.commerce.admin.web.v1.admin.coupon.response;

import com.commerce.coupon.application.info.IssuedCouponInfo;
import com.commerce.coupon.domain.IssuedCouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// app-api 본인/공개 슬라이스의 동명 응답과 같은 형상의 어드민 소유 사본이다(와이어 계약 동일).
@Schema(description = "발급 쿠폰 상세 응답")
public record IssuedCouponResponse(
        @Schema(description = "발급분 ID") UUID id,
        @Schema(description = "쿠폰 ID") UUID couponId,
        @Schema(description = "소유 회원 ID") UUID memberId,
        @Schema(description = "사용 상태") IssuedCouponStatus status,
        @Schema(description = "사용 만료 시각") Instant expiresAt,

        @Schema(description = "사용 시각", nullable = true) @Nullable
        Instant usedAt,

        @Schema(description = "사용 주문 ID", nullable = true) @Nullable
        UUID orderId,

        @Schema(description = "무효화 시각", nullable = true) @Nullable
        Instant revokedAt,

        @Schema(description = "무효화 사유", nullable = true) @Nullable
        String revokeReason) {

    /** 발급 쿠폰 조회 모델에서 응답을 만든다. */
    public static IssuedCouponResponse from(IssuedCouponInfo issued) {
        return new IssuedCouponResponse(
                issued.id(),
                issued.couponId(),
                issued.memberId(),
                issued.status(),
                issued.expiresAt(),
                issued.usedAt(),
                issued.orderId(),
                issued.revokedAt(),
                issued.revokeReason());
    }
}

package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.info.IssuedCouponInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

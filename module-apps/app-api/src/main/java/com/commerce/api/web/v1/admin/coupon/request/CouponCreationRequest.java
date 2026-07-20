package com.commerce.api.web.v1.admin.coupon.request;

import com.commerce.coupon.entity.ValidityPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 쿠폰 정책 생성 요청이다. 발급 가능 기간은 {@code validFrom < validUntil}이다. */
@Schema(description = "쿠폰 정책 생성 요청")
public record CouponCreationRequest(
        @Schema(description = "쿠폰명") @NotBlank String name,
        @Schema(description = "할인 정책") @NotNull @Valid DiscountRequest discount,

        @Schema(description = "최소 주문 금액(원 단위)") @NotNull @PositiveOrZero
        Long minOrderAmount,

        @Schema(description = "발급 가능 시작 시각") @NotNull Instant validFrom,
        @Schema(description = "발급 가능 종료 시각") @NotNull Instant validUntil,
        @Schema(description = "사용 창(일)") @NotNull @Positive Integer usageValidDays,

        @Schema(description = "발급 한도(없으면 무제한)", nullable = true) @Nullable @Positive
        Integer maxIssuance) {

    /** 도메인 발급 가능 기간 값 객체로 변환한다. */
    public ValidityPeriod toValidityPeriod() {
        return ValidityPeriod.of(validFrom, validUntil);
    }
}

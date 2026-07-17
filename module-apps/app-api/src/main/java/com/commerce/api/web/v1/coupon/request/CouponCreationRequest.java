package com.commerce.api.web.v1.coupon.request;

import com.commerce.coupon.entity.ValidityPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 정책 생성 요청이다.
 *
 * <p>최소 주문 금액은 0 이상, 사용 창(일)은 1 이상이다. 발급 가능 기간은 {@code validFrom < validUntil}이며
 * 도메인이 검증한다. 발급 한도는 선택이며 존재하면 1 이상, 없으면 무제한이다.
 */
public record CouponCreationRequest(
        @NotBlank String name,
        @NotNull @Valid DiscountRequest discount,
        @NotNull @PositiveOrZero Long minOrderAmount,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil,
        @NotNull @Positive Integer usageValidDays,
        @Nullable @Positive Integer maxIssuance) {

    /** 도메인 발급 가능 기간 값 객체로 변환한다. */
    public ValidityPeriod toValidityPeriod() {
        return ValidityPeriod.of(validFrom, validUntil);
    }
}

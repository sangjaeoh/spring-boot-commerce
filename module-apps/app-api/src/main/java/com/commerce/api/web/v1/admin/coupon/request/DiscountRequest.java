package com.commerce.api.web.v1.admin.coupon.request;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

/**
 * 쿠폰 할인 정책 요청이다. 판별 형 정액({@code FIXED})·정률({@code RATE})을 평탄한 nullable 필드로 싣는다.
 *
 * <p>형별 조합 검증은 도메인 값 객체가 소유한다. 잘못된 조합은 변환 시 도메인이 거부한다. 정액 할인액과 정률 상한은
 * 존재하면 양수여야 한다.
 */
@Schema(description = "쿠폰 할인 정책 요청")
public record DiscountRequest(
        @Schema(description = "할인 형(정액 FIXED·정률 RATE)") @NotNull
        DiscountType type,

        @Schema(description = "정액 할인액(원 단위)", nullable = true) @Nullable @Positive
        Long amount,

        @Schema(description = "할인율(%)", nullable = true) @Nullable
        Integer percent,

        @Schema(description = "정률 할인 상한(원 단위)", nullable = true) @Nullable @Positive
        Long maxCap) {

    /** 도메인 할인 값 객체로 변환한다. 형별 조합 불법은 도메인이 거부한다. */
    public Discount toDiscount() {
        return new Discount(
                type, amount == null ? null : Money.of(amount), percent, maxCap == null ? null : Money.of(maxCap));
    }
}

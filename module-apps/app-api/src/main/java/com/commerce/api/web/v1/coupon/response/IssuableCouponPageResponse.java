package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.CouponInfo;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "발급 가능 쿠폰 목록 페이지 응답")
public record IssuableCouponPageResponse(
        @Schema(description = "발급 가능 쿠폰 목록") List<IssuableCouponResponse> coupons,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public IssuableCouponPageResponse {
        coupons = List.copyOf(coupons);
    }

    /** 발급 가능 쿠폰 조회 페이지에서 응답을 만든다. */
    public static IssuableCouponPageResponse from(Page<CouponInfo> page) {
        return new IssuableCouponPageResponse(
                page.getContent().stream().map(IssuableCouponResponse::from).toList(), PaginationResponse.from(page));
    }
}

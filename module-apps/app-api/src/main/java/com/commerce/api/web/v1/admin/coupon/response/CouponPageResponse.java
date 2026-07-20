package com.commerce.api.web.v1.admin.coupon.response;

import com.commerce.coupon.info.CouponInfo;
import com.commerce.web.paging.PaginationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "쿠폰 정책 목록 페이지 응답")
public record CouponPageResponse(
        @Schema(description = "쿠폰 목록") List<CouponResponse> coupons,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public CouponPageResponse {
        coupons = List.copyOf(coupons);
    }

    /** 쿠폰 정책 조회 페이지에서 응답을 만든다. */
    public static CouponPageResponse from(Page<CouponInfo> page) {
        return new CouponPageResponse(
                page.getContent().stream().map(CouponResponse::from).toList(), PaginationResponse.from(page));
    }
}

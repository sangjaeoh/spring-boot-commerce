package com.commerce.app.admin.web.v1.admin.coupon.response;

import com.commerce.common.web.paging.PaginationResponse;
import com.commerce.domain.coupon.application.info.IssuedCouponInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "정책별 발급 쿠폰 목록 페이지 응답")
public record IssuedCouponPageResponse(
        @Schema(description = "발급 쿠폰 목록") List<IssuedCouponResponse> issuedCoupons,
        @Schema(description = "페이지 메타") PaginationResponse page) {

    public IssuedCouponPageResponse {
        issuedCoupons = List.copyOf(issuedCoupons);
    }

    /** 발급 쿠폰 조회 페이지에서 응답을 만든다. */
    public static IssuedCouponPageResponse from(Page<IssuedCouponInfo> page) {
        return new IssuedCouponPageResponse(
                page.getContent().stream().map(IssuedCouponResponse::from).toList(), PaginationResponse.from(page));
    }
}

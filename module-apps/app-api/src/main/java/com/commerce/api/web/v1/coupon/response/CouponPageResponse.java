package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.CouponInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 쿠폰 정책 목록 페이지 응답이다. */
@Schema(description = "쿠폰 정책 목록 페이지 응답")
public record CouponPageResponse(
        @Schema(description = "쿠폰 목록") List<CouponResponse> coupons,
        @Schema(description = "현재 페이지 번호") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 요소 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

    public CouponPageResponse {
        coupons = List.copyOf(coupons);
    }

    public static CouponPageResponse from(Page<CouponInfo> page) {
        return new CouponPageResponse(
                page.getContent().stream().map(CouponResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

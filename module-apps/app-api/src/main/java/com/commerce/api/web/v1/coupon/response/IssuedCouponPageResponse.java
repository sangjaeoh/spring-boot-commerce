package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.IssuedCouponInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 정책별 발급 쿠폰 목록 페이지 응답이다. */
@Schema(description = "정책별 발급 쿠폰 목록 페이지 응답")
public record IssuedCouponPageResponse(
        @Schema(description = "발급 쿠폰 목록") List<IssuedCouponResponse> issuedCoupons,
        @Schema(description = "현재 페이지 번호") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 요소 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages) {

    public IssuedCouponPageResponse {
        issuedCoupons = List.copyOf(issuedCoupons);
    }

    public static IssuedCouponPageResponse from(Page<IssuedCouponInfo> page) {
        return new IssuedCouponPageResponse(
                page.getContent().stream().map(IssuedCouponResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

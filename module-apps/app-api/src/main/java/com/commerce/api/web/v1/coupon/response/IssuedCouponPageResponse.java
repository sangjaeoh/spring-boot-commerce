package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.IssuedCouponInfo;
import java.util.List;
import org.springframework.data.domain.Page;

/** 정책별 발급 쿠폰 목록 페이지 응답이다. */
public record IssuedCouponPageResponse(
        List<IssuedCouponResponse> issuedCoupons, int page, int size, long totalElements, int totalPages) {

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

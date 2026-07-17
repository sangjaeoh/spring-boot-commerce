package com.commerce.api.web.v1.coupon.response;

import com.commerce.coupon.info.CouponInfo;
import java.util.List;
import org.springframework.data.domain.Page;

/** 쿠폰 정책 목록 페이지 응답이다. */
public record CouponPageResponse(List<CouponResponse> coupons, int page, int size, long totalElements, int totalPages) {

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

package com.commerce.api.presentation.v1;

import com.commerce.api.presentation.v1.response.IssuedCouponResponse;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.web.auth.AuthUser;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발급 쿠폰 조회 엔드포인트다.
 *
 * <p>본인용 표면이라 소유 회원은 토큰 주체({@link AuthUser})에서 도출하고 미인증 요청은 401로 거부된다.
 * 발급 쿠폰 도메인 Reader에 위임해 본인 발급분을 단건·목록 조회한다. 미소유는 미존재로 취급하며, 도메인이
 * 던지는 미존재 예외를 전역 핸들러가 problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/issued-coupons")
public class IssuedCouponController {

    private final IssuedCouponReader issuedCouponReader;

    public IssuedCouponController(IssuedCouponReader issuedCouponReader) {
        this.issuedCouponReader = issuedCouponReader;
    }

    /** 본인 발급분을 조회한다. */
    @GetMapping("/{issuedCouponId}")
    public IssuedCouponResponse getIssuedCoupon(AuthUser authUser, @PathVariable UUID issuedCouponId) {
        return IssuedCouponResponse.from(issuedCouponReader.getIssuedCoupon(issuedCouponId, authUser.memberId()));
    }

    /** 본인 발급 쿠폰 목록을 최신순으로 조회한다. 없으면 빈 목록이다. */
    @GetMapping
    public List<IssuedCouponResponse> getIssuedCoupons(AuthUser authUser) {
        return issuedCouponReader.getIssuedCouponsByMember(authUser.memberId()).stream()
                .map(IssuedCouponResponse::from)
                .toList();
    }
}

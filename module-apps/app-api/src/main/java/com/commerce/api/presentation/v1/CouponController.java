package com.commerce.api.presentation.v1;

import com.commerce.api.facade.CouponIssuanceFacade;
import com.commerce.api.presentation.v1.request.CouponCreationRequest;
import com.commerce.api.presentation.v1.request.CouponIssuanceRequest;
import com.commerce.api.presentation.v1.response.CouponCreationResponse;
import com.commerce.api.presentation.v1.response.CouponIssuanceResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.service.CouponAppender;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 정책 생성·발급 엔드포인트다.
 *
 * <p>생성은 쿠폰 도메인 Appender에 얇게 위임하고(할인 조합·유효 기간·사용 창 불변식은 도메인이 검증), 발급은
 * 발급 파사드에 위임해 회원 자격 게이트를 적용한다. 크로스 도메인 정책 거부·도메인 불변식 위반은 도메인/파사드가
 * 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다. 인증이 범위 밖이라 발급 대상 회원은 요청이 싣는다.
 */
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponAppender couponAppender;
    private final CouponIssuanceFacade couponIssuanceFacade;

    public CouponController(CouponAppender couponAppender, CouponIssuanceFacade couponIssuanceFacade) {
        this.couponAppender = couponAppender;
        this.couponIssuanceFacade = couponIssuanceFacade;
    }

    /** 쿠폰 정책을 발급 가능 상태로 생성하고 생성된 쿠폰 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponCreationResponse create(@Valid @RequestBody CouponCreationRequest request) {
        UUID couponId = couponAppender.create(
                request.name(),
                request.discount().toDiscount(),
                Money.of(request.minOrderAmount()),
                request.toValidityPeriod(),
                request.usageValidDays());
        return CouponCreationResponse.from(couponId);
    }

    /** 회원에게 쿠폰을 발급하고 발급분 ID를 반환한다. */
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssuanceResponse issue(
            @PathVariable UUID couponId, @Valid @RequestBody CouponIssuanceRequest request) {
        UUID issuedCouponId = couponIssuanceFacade.issue(couponId, request.memberId());
        return CouponIssuanceResponse.from(issuedCouponId);
    }
}

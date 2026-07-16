package com.commerce.api.presentation.v1;

import com.commerce.api.facade.CouponIssuanceFacade;
import com.commerce.api.presentation.v1.request.CouponCreationRequest;
import com.commerce.api.presentation.v1.response.CouponCreationResponse;
import com.commerce.api.presentation.v1.response.CouponIssuanceResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.CouponModifier;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
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
 * 쿠폰 정책 생성·발급·전환(중지·재개) 엔드포인트다.
 *
 * <p>생성은 쿠폰 도메인 Appender에 얇게 위임하고(할인 조합·유효 기간·사용 창 불변식은 도메인이 검증), 발급은
 * 본인용 셀프서비스(셀프 클레임)라 대상 회원을 토큰 주체({@link AuthUser})에서 도출해 발급 파사드에 위임하고
 * 회원 자격 게이트를 적용한다(미인증은 401). 생성·전환은 관리자 표면이라 관리자 토큰만 허용한다({@link AdminOnly}).
 * 전환은 단일 도메인 쓰기라 파사드 없이 쿠폰 도메인 Modifier에 얇게 위임하며, 중지는 신규 발급만 막고
 * 기발급분 사용에는 소급하지 않는다. 크로스 도메인 정책 거부·도메인 불변식 위반은 도메인/파사드가 던지는
 * 예외를 전역 핸들러가 problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponAppender couponAppender;
    private final CouponModifier couponModifier;
    private final CouponIssuanceFacade couponIssuanceFacade;

    public CouponController(
            CouponAppender couponAppender, CouponModifier couponModifier, CouponIssuanceFacade couponIssuanceFacade) {
        this.couponAppender = couponAppender;
        this.couponModifier = couponModifier;
        this.couponIssuanceFacade = couponIssuanceFacade;
    }

    /** 쿠폰 정책을 발급 가능 상태로 생성하고 생성된 쿠폰 ID를 반환한다. */
    @AdminOnly
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponCreationResponse create(@Valid @RequestBody CouponCreationRequest request) {
        UUID couponId = couponAppender.create(
                request.name(),
                request.discount().toDiscount(),
                Money.of(request.minOrderAmount()),
                request.toValidityPeriod(),
                request.usageValidDays(),
                request.maxIssuance());
        return CouponCreationResponse.from(couponId);
    }

    /** 본인에게 쿠폰을 발급하고 발급분 ID를 반환한다. */
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssuanceResponse issue(AuthUser authUser, @PathVariable UUID couponId) {
        UUID issuedCouponId = couponIssuanceFacade.issue(couponId, authUser.memberId());
        return CouponIssuanceResponse.from(issuedCouponId);
    }

    /** 쿠폰 정책의 신규 발급을 중지한다. 기발급분 사용에는 소급하지 않는다. */
    @AdminOnly
    @PostMapping("/{couponId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID couponId) {
        couponModifier.disable(couponId);
    }

    /** 쿠폰 정책의 신규 발급을 재개한다. */
    @AdminOnly
    @PostMapping("/{couponId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable UUID couponId) {
        couponModifier.enable(couponId);
    }
}

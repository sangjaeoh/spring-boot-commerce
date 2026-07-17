package com.commerce.api.web.v1.coupon;

import com.commerce.api.web.v1.coupon.request.IssuedCouponRevocationRequest;
import com.commerce.api.web.v1.coupon.response.DiscountPreviewResponse;
import com.commerce.api.web.v1.coupon.response.IssuedCouponResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발급 쿠폰 조회·할인 미리보기·무효화 엔드포인트다.
 *
 * <p>조회는 본인용 표면이라 소유 회원은 토큰 주체({@link AuthUser})에서 도출하고 미인증 요청은 401로 거부된다.
 * 발급 쿠폰 도메인 Reader에 위임해 본인 발급분을 단건·목록 조회한다. 미소유는 미존재로 취급하며, 도메인이
 * 던지는 미존재 예외를 전역 핸들러가 problem+json으로 매핑한다. 할인 미리보기는 주문 금액에 대한 예상 할인을
 * 계산만 하고 상태를 바꾸지 않는다. 무효화는 관리자 표면이라 관리자 토큰만
 * 허용하고({@link AdminOnly}) 단일 도메인 쓰기라 파사드 없이 발급 쿠폰 도메인 Modifier에 얇게 위임한다.
 */
@RestController
@RequestMapping("/api/v1/issued-coupons")
public class IssuedCouponController {

    // 정률 곱셈(orderAmount × percent)의 long 오버플로를 경계에서 배제하는 상한(1조 원).
    private static final long MAX_ORDER_AMOUNT = 1_000_000_000_000L;

    private final IssuedCouponReader issuedCouponReader;
    private final IssuedCouponModifier issuedCouponModifier;

    public IssuedCouponController(IssuedCouponReader issuedCouponReader, IssuedCouponModifier issuedCouponModifier) {
        this.issuedCouponReader = issuedCouponReader;
        this.issuedCouponModifier = issuedCouponModifier;
    }

    /** 본인 발급분을 조회한다. */
    @GetMapping("/{issuedCouponId}")
    public IssuedCouponResponse getIssuedCoupon(AuthUser authUser, @PathVariable UUID issuedCouponId) {
        return IssuedCouponResponse.from(issuedCouponReader.getIssuedCoupon(issuedCouponId, authUser.memberId()));
    }

    /**
     * 본인 발급분의 주문 금액 기준 예상 할인을 조회한다. 계산만 하고 상태를 바꾸지 않으며, 적용 불가는 오류가
     * 아니라 사유와 0원으로 싣는다. 결과는 보증이 아니며 체크아웃 시점 재검증이 진실이다.
     */
    @GetMapping("/{issuedCouponId}/discount-preview")
    public DiscountPreviewResponse getDiscountPreview(
            AuthUser authUser,
            @PathVariable UUID issuedCouponId,
            @RequestParam @Min(0) @Max(MAX_ORDER_AMOUNT) long orderAmount) {
        return DiscountPreviewResponse.from(
                issuedCouponReader.getDiscountPreview(issuedCouponId, authUser.memberId(), Money.of(orderAmount)));
    }

    /** 본인 발급 쿠폰 목록을 최신순으로 조회한다. 없으면 빈 목록이다. */
    @GetMapping
    public List<IssuedCouponResponse> getIssuedCoupons(AuthUser authUser) {
        return issuedCouponReader.getIssuedCouponsByMember(authUser.memberId()).stream()
                .map(IssuedCouponResponse::from)
                .toList();
    }

    /** 발급분을 무효화한다. 무효화된 발급분은 사용이 거부되고, 사용된 발급분은 무효화가 거부된다. */
    @AdminOnly
    @PostMapping("/{issuedCouponId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID issuedCouponId, @Valid @RequestBody IssuedCouponRevocationRequest request) {
        issuedCouponModifier.revoke(issuedCouponId, request.reason());
    }
}

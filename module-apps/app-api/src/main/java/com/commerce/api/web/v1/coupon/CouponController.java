package com.commerce.api.web.v1.coupon;

import com.commerce.api.facade.CouponIssuanceFacade;
import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.coupon.response.CouponIssuanceResponse;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 발급(셀프 클레임) 엔드포인트다.
 *
 * <p>발급은 본인용 셀프서비스라 대상 회원을 토큰 주체({@link AuthUser})에서 도출해 발급 파사드에 위임하고
 * 회원 자격 게이트를 적용한다. 크로스 도메인 정책 거부·도메인 불변식 위반은 도메인/파사드가 던지는 예외를
 * 전역 핸들러가 problem+json으로 매핑한다.
 */
@Tag(name = "쿠폰", description = "쿠폰 발급(셀프 클레임)")
@Authenticated
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponIssuanceFacade couponIssuanceFacade;

    public CouponController(CouponIssuanceFacade couponIssuanceFacade) {
        this.couponIssuanceFacade = couponIssuanceFacade;
    }

    @Operation(summary = "쿠폰 발급", description = "본인에게 쿠폰을 발급하고 발급분 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "발급됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "쿠폰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 발급됨 또는 발급 중지·발급 기간 외·한도 소진·동시성 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{couponId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponIssuanceResponse issue(
            AuthUser authUser, @Parameter(description = "쿠폰 ID") @PathVariable UUID couponId) {
        UUID issuedCouponId = couponIssuanceFacade.issue(couponId, authUser.memberId());
        return CouponIssuanceResponse.from(issuedCouponId);
    }
}

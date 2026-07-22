package com.commerce.api.web.v1.coupon;

import com.commerce.api.facade.CouponIssuanceFacade;
import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.coupon.response.CouponIssuanceResponse;
import com.commerce.api.web.v1.coupon.response.IssuableCouponPageResponse;
import com.commerce.coupon.application.provided.CouponReader;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.paging.PaginationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쿠폰 목록 조회·발급(셀프 클레임) 엔드포인트다. */
@Tag(name = "쿠폰", description = "쿠폰 목록 조회·발급(셀프 클레임)")
@Authenticated
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponIssuanceFacade couponIssuanceFacade;
    private final CouponReader couponReader;

    public CouponController(CouponIssuanceFacade couponIssuanceFacade, CouponReader couponReader) {
        this.couponIssuanceFacade = couponIssuanceFacade;
        this.couponReader = couponReader;
    }

    @Operation(summary = "발급 가능 쿠폰 목록 조회", description = "발급 가능한(활성·기간 내·한도 미소진) 쿠폰 목록을 최신 등록순 페이지로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "페이지 파라미터가 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public IssuableCouponPageResponse getIssuableCoupons(@Valid @ParameterObject PaginationRequest pagination) {
        return IssuableCouponPageResponse.from(
                couponReader.getIssuableCoupons(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
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

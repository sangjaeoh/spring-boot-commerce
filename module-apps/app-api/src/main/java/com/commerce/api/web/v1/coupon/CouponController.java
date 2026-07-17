package com.commerce.api.web.v1.coupon;

import com.commerce.api.facade.CouponIssuanceFacade;
import com.commerce.api.web.v1.coupon.request.CouponCreationRequest;
import com.commerce.api.web.v1.coupon.response.CouponCreationResponse;
import com.commerce.api.web.v1.coupon.response.CouponIssuanceResponse;
import com.commerce.api.web.v1.coupon.response.CouponPageResponse;
import com.commerce.api.web.v1.coupon.response.IssuedCouponPageResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.CouponModifier;
import com.commerce.coupon.service.CouponReader;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.web.auth.AdminOnly;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 정책 생성·조회·발급·전환(중지·재개) 엔드포인트다.
 *
 * <p>생성은 쿠폰 도메인 Appender에 얇게 위임하고(할인 조합·유효 기간·사용 창 불변식은 도메인이 검증), 발급은
 * 본인용 셀프서비스(셀프 클레임)라 대상 회원을 토큰 주체({@link AuthUser})에서 도출해 발급 파사드에 위임하고
 * 회원 자격 게이트를 적용한다(미인증은 401). 생성·전환은 관리자 표면이라 관리자 토큰만 허용한다({@link AdminOnly}).
 * 전환은 단일 도메인 쓰기라 파사드 없이 쿠폰 도메인 Modifier에 얇게 위임하며, 중지는 신규 발급만 막고
 * 기발급분 사용에는 소급하지 않는다. 크로스 도메인 정책 거부·도메인 불변식 위반은 도메인/파사드가 던지는
 * 예외를 전역 핸들러가 problem+json으로 매핑한다.
 */
@Tag(name = "쿠폰", description = "쿠폰 정책 생성·조회·발급·전환")
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponAppender couponAppender;
    private final CouponModifier couponModifier;
    private final CouponReader couponReader;
    private final IssuedCouponReader issuedCouponReader;
    private final CouponIssuanceFacade couponIssuanceFacade;

    public CouponController(
            CouponAppender couponAppender,
            CouponModifier couponModifier,
            CouponReader couponReader,
            IssuedCouponReader issuedCouponReader,
            CouponIssuanceFacade couponIssuanceFacade) {
        this.couponAppender = couponAppender;
        this.couponModifier = couponModifier;
        this.couponReader = couponReader;
        this.issuedCouponReader = issuedCouponReader;
        this.couponIssuanceFacade = couponIssuanceFacade;
    }

    /** 쿠폰 정책을 발급 가능 상태로 생성하고 생성된 쿠폰 ID를 반환한다. */
    @Operation(summary = "쿠폰 생성", description = "쿠폰 정책을 발급 가능 상태로 생성하고 생성된 쿠폰 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값이 유효하지 않음 또는 할인·유효 기간 등 도메인 불변식 위반",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
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

    /** 쿠폰 정책 목록을 최신 등록순 페이지로 조회한다. */
    @Operation(summary = "쿠폰 목록 조회", description = "쿠폰 정책 목록을 최신 등록순 페이지로 조회한다.")
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
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @GetMapping
    public CouponPageResponse getCoupons(@Valid @ParameterObject PaginationRequest pagination) {
        return CouponPageResponse.from(
                couponReader.getCoupons(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 정책의 발급분 목록을 최신순 페이지로 조회한다(무효화 대상 발급분 발견). */
    @Operation(summary = "정책별 발급분 조회", description = "정책의 발급분 목록을 최신순 페이지로 조회한다.")
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
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @GetMapping("/{couponId}/issues")
    public IssuedCouponPageResponse getIssues(
            @Parameter(description = "쿠폰 ID") @PathVariable UUID couponId,
            @Valid @ParameterObject PaginationRequest pagination) {
        return IssuedCouponPageResponse.from(issuedCouponReader.getIssuedCouponsByCoupon(
                couponId, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 본인에게 쿠폰을 발급하고 발급분 ID를 반환한다. */
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

    /** 쿠폰 정책의 신규 발급을 중지한다. 기발급분 사용에는 소급하지 않는다. */
    @Operation(summary = "발급 중지", description = "쿠폰 정책의 신규 발급을 중지한다. 기발급분 사용에는 소급하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "중지됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "쿠폰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{couponId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Parameter(description = "쿠폰 ID") @PathVariable UUID couponId) {
        couponModifier.disable(couponId);
    }

    /** 쿠폰 정책의 신규 발급을 재개한다. */
    @Operation(summary = "발급 재개", description = "쿠폰 정책의 신규 발급을 재개한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "재개됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "쿠폰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{couponId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@Parameter(description = "쿠폰 ID") @PathVariable UUID couponId) {
        couponModifier.enable(couponId);
    }
}

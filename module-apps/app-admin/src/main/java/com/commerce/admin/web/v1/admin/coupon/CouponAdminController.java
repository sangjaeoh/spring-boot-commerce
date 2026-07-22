package com.commerce.admin.web.v1.admin.coupon;

import com.commerce.admin.web.auth.Admin;
import com.commerce.admin.web.v1.admin.coupon.request.CouponCreationRequest;
import com.commerce.admin.web.v1.admin.coupon.response.CouponCreationResponse;
import com.commerce.admin.web.v1.admin.coupon.response.CouponPageResponse;
import com.commerce.admin.web.v1.admin.coupon.response.IssuedCouponPageResponse;
import com.commerce.coupon.application.provided.CouponAppender;
import com.commerce.coupon.application.provided.CouponModifier;
import com.commerce.coupon.application.provided.CouponReader;
import com.commerce.coupon.application.provided.IssuedCouponReader;
import com.commerce.shared.entity.Money;
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

/** 쿠폰 정책 생성·목록 조회·정책별 발급분 조회·전환(중지·재개)의 관리자 엔드포인트다. */
@Tag(name = "쿠폰 관리", description = "쿠폰 정책 생성·목록 조회·발급분 조회·전환")
@Admin
@RestController
@RequestMapping("/api/v1/admin/coupons")
public class CouponAdminController {

    private final CouponAppender couponAppender;
    private final CouponModifier couponModifier;
    private final CouponReader couponReader;
    private final IssuedCouponReader issuedCouponReader;

    public CouponAdminController(
            CouponAppender couponAppender,
            CouponModifier couponModifier,
            CouponReader couponReader,
            IssuedCouponReader issuedCouponReader) {
        this.couponAppender = couponAppender;
        this.couponModifier = couponModifier;
        this.couponReader = couponReader;
        this.issuedCouponReader = issuedCouponReader;
    }

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
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
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
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public CouponPageResponse getCoupons(@Valid @ParameterObject PaginationRequest pagination) {
        return CouponPageResponse.from(
                couponReader.getCoupons(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

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
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{couponId}/issues")
    public IssuedCouponPageResponse getIssues(
            @Parameter(description = "쿠폰 ID") @PathVariable UUID couponId,
            @Valid @ParameterObject PaginationRequest pagination) {
        return IssuedCouponPageResponse.from(issuedCouponReader.getIssuedCouponsByCoupon(
                couponId, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    @Operation(summary = "발급 중지", description = "쿠폰 정책의 신규 발급을 중지한다. 기발급분 사용에는 소급하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "중지됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
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
    @PostMapping("/{couponId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Parameter(description = "쿠폰 ID") @PathVariable UUID couponId) {
        couponModifier.disable(couponId);
    }

    @Operation(summary = "발급 재개", description = "쿠폰 정책의 신규 발급을 재개한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "재개됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
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
    @PostMapping("/{couponId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@Parameter(description = "쿠폰 ID") @PathVariable UUID couponId) {
        couponModifier.enable(couponId);
    }
}

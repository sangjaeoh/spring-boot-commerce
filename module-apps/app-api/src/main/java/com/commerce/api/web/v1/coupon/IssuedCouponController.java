package com.commerce.api.web.v1.coupon;

import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.coupon.response.DiscountPreviewResponse;
import com.commerce.api.web.v1.coupon.response.IssuedCouponResponse;
import com.commerce.core.money.Money;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발급 쿠폰 조회·할인 미리보기 엔드포인트다.
 *
 * <p>전부 본인용 표면이라 소유 회원은 토큰 주체({@link AuthUser})에서 도출하고 미인증 요청은 401로 거부된다.
 * 발급 쿠폰 도메인 Reader에 위임해 본인 발급분을 단건·목록 조회한다. 미소유는 미존재로 취급하며, 도메인이
 * 던지는 미존재 예외를 전역 핸들러가 problem+json으로 매핑한다. 할인 미리보기는 주문 금액에 대한 예상 할인을
 * 계산만 하고 상태를 바꾸지 않는다. 무효화의 관리자 표면은
 * {@link com.commerce.api.web.v1.admin.coupon.IssuedCouponAdminController}가 소유한다.
 */
@Tag(name = "발급 쿠폰", description = "발급 쿠폰 조회·할인 미리보기")
@Authenticated
@RestController
@RequestMapping("/api/v1/issued-coupons")
public class IssuedCouponController {

    // 정률 곱셈(orderAmount × percent)의 long 오버플로를 경계에서 배제하는 상한(1조 원).
    private static final long MAX_ORDER_AMOUNT = 1_000_000_000_000L;

    private final IssuedCouponReader issuedCouponReader;

    public IssuedCouponController(IssuedCouponReader issuedCouponReader) {
        this.issuedCouponReader = issuedCouponReader;
    }

    /** 본인 발급분을 조회한다. */
    @Operation(summary = "발급분 조회", description = "본인 발급분을 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "발급 쿠폰 없음(미소유 포함)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{issuedCouponId}")
    public IssuedCouponResponse getIssuedCoupon(
            AuthUser authUser, @Parameter(description = "발급 쿠폰 ID") @PathVariable UUID issuedCouponId) {
        return IssuedCouponResponse.from(issuedCouponReader.getIssuedCoupon(issuedCouponId, authUser.memberId()));
    }

    /**
     * 본인 발급분의 주문 금액 기준 예상 할인을 조회한다. 계산만 하고 상태를 바꾸지 않으며, 적용 불가는 오류가
     * 아니라 사유와 0원으로 싣는다. 결과는 보증이 아니며 체크아웃 시점 재검증이 진실이다.
     */
    @Operation(
            summary = "할인 미리보기",
            description = "본인 발급분의 주문 금액 기준 예상 할인을 조회한다. 계산만 하고 상태를 바꾸지 않으며, 적용 불가는 오류가 아니라 사유와 0원으로 싣는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "주문 금액 파라미터가 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "발급 쿠폰 없음(미소유 포함)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{issuedCouponId}/discount-preview")
    public DiscountPreviewResponse getDiscountPreview(
            AuthUser authUser,
            @Parameter(description = "발급 쿠폰 ID") @PathVariable UUID issuedCouponId,
            @Parameter(description = "할인 계산 기준 주문 금액(원)") @RequestParam @Min(0) @Max(MAX_ORDER_AMOUNT)
                    long orderAmount) {
        return DiscountPreviewResponse.from(
                issuedCouponReader.getDiscountPreview(issuedCouponId, authUser.memberId(), Money.of(orderAmount)));
    }

    /** 본인 발급 쿠폰 목록을 최신순으로 조회한다. 없으면 빈 목록이다. */
    @Operation(summary = "발급 쿠폰 목록 조회", description = "본인 발급 쿠폰 목록을 최신순으로 조회한다. 없으면 빈 목록이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public List<IssuedCouponResponse> getIssuedCoupons(AuthUser authUser) {
        return issuedCouponReader.getIssuedCouponsByMember(authUser.memberId()).stream()
                .map(IssuedCouponResponse::from)
                .toList();
    }
}

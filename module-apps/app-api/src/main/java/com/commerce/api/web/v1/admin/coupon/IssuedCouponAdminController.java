package com.commerce.api.web.v1.admin.coupon;

import com.commerce.api.web.auth.Admin;
import com.commerce.api.web.v1.admin.coupon.request.IssuedCouponRevocationRequest;
import com.commerce.coupon.service.IssuedCouponModifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발급 쿠폰 무효화의 관리자 엔드포인트다.
 *
 * <p>관리자 표면이라 관리자 토큰만 허용한다({@link Admin} — 미인증 401·비관리자 403). 본인 발급분 조회·할인
 * 미리보기의 본인용 표면은 {@link com.commerce.api.web.v1.coupon.IssuedCouponController}가 소유한다. 단일 도메인
 * 쓰기라 파사드 없이 발급 쿠폰 도메인 Modifier에 얇게 위임하고, 미존재·무효화 불가 상태는 도메인이 던지는 예외를
 * 전역 핸들러가 problem+json으로 매핑한다.
 */
@Tag(name = "발급 쿠폰 관리", description = "발급 쿠폰 무효화")
@Admin
@RestController
@RequestMapping("/api/v1/admin/issued-coupons")
public class IssuedCouponAdminController {

    private final IssuedCouponModifier issuedCouponModifier;

    public IssuedCouponAdminController(IssuedCouponModifier issuedCouponModifier) {
        this.issuedCouponModifier = issuedCouponModifier;
    }

    /** 발급분을 무효화한다. 무효화된 발급분은 사용이 거부되고, 사용된 발급분은 무효화가 거부된다. */
    @Operation(summary = "발급분 무효화", description = "발급분을 무효화한다. 무효화된 발급분은 사용이 거부되고, 사용된 발급분은 무효화가 거부된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "무효화됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값이 유효하지 않음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
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
                description = "발급 쿠폰 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "무효화할 수 없는 발급 쿠폰 상태",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{issuedCouponId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @Parameter(description = "발급 쿠폰 ID") @PathVariable UUID issuedCouponId,
            @Valid @RequestBody IssuedCouponRevocationRequest request) {
        issuedCouponModifier.revoke(issuedCouponId, request.reason());
    }
}

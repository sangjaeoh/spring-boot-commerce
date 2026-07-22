package com.commerce.admin.web.v1.admin.coupon;

import com.commerce.admin.web.auth.Admin;
import com.commerce.admin.web.v1.admin.coupon.request.IssuedCouponRevocationRequest;
import com.commerce.coupon.application.IssuedCouponModifier;
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

/** 발급 쿠폰 무효화의 관리자 엔드포인트다. */
@Tag(name = "발급 쿠폰 관리", description = "발급 쿠폰 무효화")
@Admin
@RestController
@RequestMapping("/api/v1/admin/issued-coupons")
public class IssuedCouponAdminController {

    private final IssuedCouponModifier issuedCouponModifier;

    public IssuedCouponAdminController(IssuedCouponModifier issuedCouponModifier) {
        this.issuedCouponModifier = issuedCouponModifier;
    }

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

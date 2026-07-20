package com.commerce.api.web.v1.admin.product;

import com.commerce.api.web.auth.Admin;
import com.commerce.api.web.v1.admin.product.request.VariantPriceChangeRequest;
import com.commerce.core.money.Money;
import com.commerce.product.service.ProductVariantModifier;
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

/** 상품 변형 관리(가격 변경·활성/비활성/은퇴) 엔드포인트다. */
@Tag(name = "상품 변형 관리", description = "변형 가격 변경·판매 제공/중단·은퇴")
@Admin
@RestController
@RequestMapping("/api/v1/admin/product-variants")
public class ProductVariantAdminController {

    private final ProductVariantModifier variantModifier;

    public ProductVariantAdminController(ProductVariantModifier variantModifier) {
        this.variantModifier = variantModifier;
    }

    @Operation(summary = "변형 가격 변경", description = "변형 판매가를 바꾼다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "변경됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
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
                description = "변형 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 변형 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/price-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePrice(
            @Parameter(description = "변형 ID") @PathVariable UUID variantId,
            @Valid @RequestBody VariantPriceChangeRequest request) {
        variantModifier.changePrice(variantId, Money.of(request.price()));
    }

    @Operation(summary = "변형 판매 제공", description = "변형을 판매 제공한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "판매 제공됨"),
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
                description = "변형 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 변형 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        variantModifier.enable(variantId);
    }

    @Operation(summary = "변형 판매 중단", description = "변형 판매 제공을 중단한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "중단됨"),
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
                description = "변형 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 변형 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        variantModifier.disable(variantId);
    }

    @Operation(summary = "변형 은퇴", description = "변형을 은퇴시킨다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "은퇴됨"),
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
                description = "변형 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 변형 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        variantModifier.retire(variantId);
    }
}

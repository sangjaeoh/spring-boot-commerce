package com.commerce.api.web.v1.admin.product;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.web.auth.Admin;
import com.commerce.api.web.v1.admin.product.request.ProductEditRequest;
import com.commerce.api.web.v1.admin.product.request.ProductRegistrationRequest;
import com.commerce.api.web.v1.admin.product.request.VariantRegistrationRequest;
import com.commerce.api.web.v1.admin.product.response.ProductAdminPageResponse;
import com.commerce.api.web.v1.admin.product.response.ProductRegistrationResponse;
import com.commerce.api.web.v1.admin.product.response.VariantRegistrationResponse;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductRemover;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 상품 등록·추가 변형 등록·관리(노출·숨김·편집·논리삭제)·숨김 포함 목록 조회의 관리자 엔드포인트다. */
@Tag(name = "상품 관리", description = "상품 등록·관리·숨김 포함 목록 조회")
@Admin
@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductAdminController {

    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductReader productReader;
    private final ProductModifier productModifier;
    private final ProductRemover productRemover;

    public ProductAdminController(
            ProductRegistrationFacade productRegistrationFacade,
            ProductReader productReader,
            ProductModifier productModifier,
            ProductRemover productRemover) {
        this.productRegistrationFacade = productRegistrationFacade;
        this.productReader = productReader;
        this.productModifier = productModifier;
        this.productRemover = productRemover;
    }

    @Operation(summary = "상품 등록", description = "상품·첫 변형·초기 재고를 시딩하고 등록된 상품 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "상품 등록됨"),
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
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRegistrationResponse register(@Valid @RequestBody ProductRegistrationRequest request) {
        UUID productId = productRegistrationFacade.registerProduct(
                request.name(),
                request.description(),
                request.categoryId(),
                Money.of(request.price()),
                request.toProductOptions(),
                request.initialQuantity());
        return ProductRegistrationResponse.from(productId);
    }

    @Operation(summary = "추가 변형 등록", description = "기존 상품에 변형·재고를 시딩하고 등록된 변형 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "변형 등록됨"),
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이미 존재하는 옵션 조합",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public VariantRegistrationResponse addVariant(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody VariantRegistrationRequest request) {
        UUID variantId = productRegistrationFacade.addVariant(
                productId, Money.of(request.price()), request.toProductOptions(), request.initialQuantity());
        return VariantRegistrationResponse.from(variantId);
    }

    @Operation(summary = "관리자 상품 목록 조회", description = "미삭제 상품 목록을 숨김 포함 최신 등록순 페이지로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
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
    })
    @GetMapping
    public ProductAdminPageResponse getProductsForAdmin(@Valid @ParameterObject PaginationRequest pagination) {
        return ProductAdminPageResponse.from(
                productReader.getPage(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    @Operation(summary = "상품 노출", description = "상품을 노출한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "노출됨"),
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상품 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{productId}/show")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void show(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productModifier.show(productId);
    }

    @Operation(summary = "상품 숨김", description = "상품을 숨긴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "숨겨짐"),
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상품 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{productId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productModifier.hide(productId);
    }

    @Operation(summary = "상품 편집", description = "상품명·상세 설명·분류를 바꾼다. 분류를 생략하면 미분류로 해제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "편집됨"),
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
                description = "상품 또는 카테고리 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PatchMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void edit(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody ProductEditRequest request) {
        productModifier.rename(productId, request.name());
        productModifier.changeDescription(productId, request.description());
        productModifier.assignCategory(productId, request.categoryId());
    }

    @Operation(summary = "상품 논리삭제", description = "상품을 논리삭제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "논리삭제됨"),
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productRemover.delete(productId);
    }
}

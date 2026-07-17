package com.commerce.api.web.v1.product;

import com.commerce.api.facade.ProductCatalogFacade;
import com.commerce.api.facade.ProductDetailFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.web.v1.product.request.ProductEditRequest;
import com.commerce.api.web.v1.product.request.ProductRegistrationRequest;
import com.commerce.api.web.v1.product.request.VariantRegistrationRequest;
import com.commerce.api.web.v1.product.response.ProductAdminPageResponse;
import com.commerce.api.web.v1.product.response.ProductDetailResponse;
import com.commerce.api.web.v1.product.response.ProductPageResponse;
import com.commerce.api.web.v1.product.response.ProductRegistrationResponse;
import com.commerce.api.web.v1.product.response.VariantRegistrationResponse;
import com.commerce.core.money.Money;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductRemover;
import com.commerce.web.auth.AdminOnly;
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

/**
 * 상품 등록·추가 변형 등록·목록·상세 조회·관리(노출·숨김·편집·논리삭제) 엔드포인트다.
 *
 * <p>등록·추가 변형 등록은 상품 등록 파사드에 얇게 위임한다(변형·초기 재고를 순차 시딩). 등록·관리는
 * 관리자 표면이라 관리자 토큰만 허용하고({@link AdminOnly}), 목록·상세 조회는 공개다(비로그인 쇼핑). 조회는
 * 각 파사드에 위임해 ACTIVE 변형·재고 파생(주문가능·품절·대표가)을 합성하고, 컨트롤러는 요청·결과를 DTO로 변환만 한다.
 * 관리는 단일 도메인 쓰기라 파사드 없이 상품 도메인 Modifier·Remover에 얇게 위임하고, 미존재·허용되지 않은 전이는
 * 도메인이 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다. 편집은 기존 주문 스냅샷에 영향을 주지 않고,
 * 논리삭제는 변형을 연쇄 삭제하지 않는다.
 */
@Tag(name = "상품", description = "상품 등록·조회·관리")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductCatalogFacade productCatalogFacade;
    private final ProductDetailFacade productDetailFacade;
    private final ProductReader productReader;
    private final ProductModifier productModifier;
    private final ProductRemover productRemover;

    public ProductController(
            ProductRegistrationFacade productRegistrationFacade,
            ProductCatalogFacade productCatalogFacade,
            ProductDetailFacade productDetailFacade,
            ProductReader productReader,
            ProductModifier productModifier,
            ProductRemover productRemover) {
        this.productRegistrationFacade = productRegistrationFacade;
        this.productCatalogFacade = productCatalogFacade;
        this.productDetailFacade = productDetailFacade;
        this.productReader = productReader;
        this.productModifier = productModifier;
        this.productRemover = productRemover;
    }

    /** 상품·첫 변형·초기 재고를 시딩하고 등록된 상품 ID를 반환한다. */
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
    @AdminOnly
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRegistrationResponse register(@Valid @RequestBody ProductRegistrationRequest request) {
        UUID productId = productRegistrationFacade.registerProduct(
                request.name(),
                request.description(),
                Money.of(request.price()),
                request.toProductOptions(),
                request.initialQuantity());
        return ProductRegistrationResponse.from(productId);
    }

    /** 기존 상품에 변형·재고를 시딩하고 등록된 변형 ID를 반환한다. */
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
    @AdminOnly
    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public VariantRegistrationResponse addVariant(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody VariantRegistrationRequest request) {
        UUID variantId = productRegistrationFacade.addVariant(
                productId, Money.of(request.price()), request.toProductOptions(), request.initialQuantity());
        return VariantRegistrationResponse.from(variantId);
    }

    /** 노출 상품 목록을 대표가·품절과 함께 최신 등록순 페이지로 조회한다. */
    @Operation(summary = "상품 목록 조회", description = "노출 상품 목록을 대표가·품절과 함께 최신 등록순 페이지로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public ProductPageResponse getProducts(@Valid @ParameterObject PaginationRequest pagination) {
        return ProductPageResponse.from(
                productCatalogFacade.getCatalogPage(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 미삭제 상품 목록을 숨김 포함 최신 등록순 페이지로 조회한다(관리 대상 발견). */
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
    @AdminOnly
    @GetMapping("/admin")
    public ProductAdminPageResponse getProductsForAdmin(@Valid @ParameterObject PaginationRequest pagination) {
        return ProductAdminPageResponse.from(
                productReader.getPage(PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 상품 상세를 ACTIVE 변형·주문가능·품절·대표가와 함께 조회한다. */
    @Operation(summary = "상품 상세 조회", description = "상품 상세를 ACTIVE 변형·주문가능·품절·대표가와 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "404",
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{productId}")
    public ProductDetailResponse getProduct(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        return ProductDetailResponse.from(productDetailFacade.getProductDetail(productId));
    }

    /** 상품을 노출한다. */
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
    @AdminOnly
    @PostMapping("/{productId}/show")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void show(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productModifier.show(productId);
    }

    /** 상품을 숨긴다. */
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
    @AdminOnly
    @PostMapping("/{productId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productModifier.hide(productId);
    }

    /** 상품명·상세 설명을 바꾼다. */
    @Operation(summary = "상품 편집", description = "상품명·상세 설명을 바꾼다.")
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PatchMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void edit(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Valid @RequestBody ProductEditRequest request) {
        productModifier.rename(productId, request.name());
        productModifier.changeDescription(productId, request.description());
    }

    /** 상품을 논리삭제한다. */
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
    @AdminOnly
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "상품 ID") @PathVariable UUID productId) {
        productRemover.delete(productId);
    }
}

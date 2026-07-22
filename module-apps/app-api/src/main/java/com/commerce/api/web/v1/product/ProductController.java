package com.commerce.api.web.v1.product;

import com.commerce.api.facade.ProductCatalogFacade;
import com.commerce.api.facade.ProductDetailFacade;
import com.commerce.api.facade.ProductSort;
import com.commerce.api.web.v1.product.response.ProductDetailResponse;
import com.commerce.api.web.v1.product.response.ProductPageResponse;
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
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 목록·상세 조회 엔드포인트다.
 *
 * <p>전부 공개다(비로그인 쇼핑).
 */
@Tag(name = "상품", description = "상품 목록·상세 조회")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCatalogFacade productCatalogFacade;
    private final ProductDetailFacade productDetailFacade;

    public ProductController(ProductCatalogFacade productCatalogFacade, ProductDetailFacade productDetailFacade) {
        this.productCatalogFacade = productCatalogFacade;
        this.productDetailFacade = productDetailFacade;
    }

    @Operation(
            summary = "상품 목록 조회",
            description = "노출 상품 목록을 대표가·품절과 함께 페이지로 조회한다. 상품명 키워드 검색·카테고리 필터와 최신순·가격순 정렬을 지원한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public ProductPageResponse getProducts(
            @Valid @ParameterObject PaginationRequest pagination,
            @Parameter(description = "상품명 키워드(부분 일치, 대소문자 무시). 생략하면 전체") @RequestParam(required = false) @Nullable
                    String keyword,
            @Parameter(description = "카테고리 필터. 생략하면 전체") @RequestParam(required = false) @Nullable UUID categoryId,
            @Parameter(description = "정렬 기준(생략 시 최신 등록순)") @RequestParam(defaultValue = "LATEST") ProductSort sort) {
        return ProductPageResponse.from(productCatalogFacade.getCatalogPage(
                keyword, categoryId, sort, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

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
}

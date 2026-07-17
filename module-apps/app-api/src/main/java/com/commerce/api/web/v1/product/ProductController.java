package com.commerce.api.web.v1.product;

import com.commerce.api.facade.ProductCatalogFacade;
import com.commerce.api.facade.ProductDetailFacade;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 목록·상세 조회 엔드포인트다.
 *
 * <p>전부 공개다(비로그인 쇼핑). 등록·추가 변형 등록·관리(노출·숨김·편집·논리삭제)의 관리자 표면은
 * {@link com.commerce.api.web.v1.admin.product.ProductAdminController}가 소유한다. 조회는 각 파사드에 위임해
 * ACTIVE 변형·재고 파생(주문가능·품절·대표가)을 합성하고, 컨트롤러는 요청·결과를 DTO로 변환만 한다. 미존재는
 * 도메인이 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다.
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
}

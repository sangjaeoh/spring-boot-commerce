package com.commerce.api.presentation.v1;

import com.commerce.api.facade.ProductCatalogFacade;
import com.commerce.api.facade.ProductDetailFacade;
import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.ProductRegistrationRequest;
import com.commerce.api.presentation.v1.request.VariantRegistrationRequest;
import com.commerce.api.presentation.v1.response.ProductDetailResponse;
import com.commerce.api.presentation.v1.response.ProductPageResponse;
import com.commerce.api.presentation.v1.response.ProductRegistrationResponse;
import com.commerce.api.presentation.v1.response.VariantRegistrationResponse;
import com.commerce.core.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 등록·추가 변형 등록·목록·상세 조회 엔드포인트다.
 *
 * <p>등록·추가 변형 등록은 상품 등록 파사드에 얇게 위임한다(변형·초기 재고를 순차 시딩). 목록·상세 조회는
 * 각 파사드에 위임해 ACTIVE 변형·재고 파생(주문가능·품절·대표가)을 합성하고, 컨트롤러는 요청·결과를 DTO로 변환만 한다.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductCatalogFacade productCatalogFacade;
    private final ProductDetailFacade productDetailFacade;

    public ProductController(
            ProductRegistrationFacade productRegistrationFacade,
            ProductCatalogFacade productCatalogFacade,
            ProductDetailFacade productDetailFacade) {
        this.productRegistrationFacade = productRegistrationFacade;
        this.productCatalogFacade = productCatalogFacade;
        this.productDetailFacade = productDetailFacade;
    }

    /** 상품·첫 변형·초기 재고를 시딩하고 등록된 상품 ID를 반환한다. */
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
    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public VariantRegistrationResponse addVariant(
            @PathVariable UUID productId, @Valid @RequestBody VariantRegistrationRequest request) {
        UUID variantId = productRegistrationFacade.addVariant(
                productId, Money.of(request.price()), request.toProductOptions(), request.initialQuantity());
        return VariantRegistrationResponse.from(variantId);
    }

    /** 노출 상품 목록을 대표가·품절과 함께 최신 등록순 페이지로 조회한다. */
    @GetMapping
    public ProductPageResponse getProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ProductPageResponse.from(productCatalogFacade.getCatalogPage(PageRequest.of(page, size)));
    }

    /** 상품 상세를 ACTIVE 변형·주문가능·품절·대표가와 함께 조회한다. */
    @GetMapping("/{productId}")
    public ProductDetailResponse getProduct(@PathVariable UUID productId) {
        return ProductDetailResponse.from(productDetailFacade.getProductDetail(productId));
    }
}

package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductView;
import com.commerce.product.entity.ProductStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 상세 응답이다. ACTIVE 변형·주문가능·품절·대표가를 싣는다. */
public record ProductDetailResponse(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        @Nullable Long fromPrice,
        boolean soldOut,
        List<VariantResponse> variants) {

    public ProductDetailResponse {
        variants = List.copyOf(variants);
    }

    public static ProductDetailResponse from(ProductView product) {
        return new ProductDetailResponse(
                product.id(),
                product.name(),
                product.description(),
                product.status(),
                product.fromPrice() == null ? null : product.fromPrice().amount(),
                product.soldOut(),
                product.variants().stream().map(VariantResponse::from).toList());
    }
}

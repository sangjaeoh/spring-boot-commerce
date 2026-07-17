package com.commerce.api.web.v1.product.response;

import com.commerce.api.facade.ProductSummaryView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 카탈로그 목록의 상품별 응답이다. 대표가·품절을 싣는다. */
public record ProductSummaryResponse(
        UUID id, String name, @Nullable Long fromPrice, boolean soldOut) {

    public static ProductSummaryResponse from(ProductSummaryView product) {
        return new ProductSummaryResponse(
                product.id(),
                product.name(),
                product.fromPrice() == null ? null : product.fromPrice().amount(),
                product.soldOut());
    }
}

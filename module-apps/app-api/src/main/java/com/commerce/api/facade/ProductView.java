package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 상세 합성 뷰다.
 *
 * @param fromPrice ACTIVE 변형 최저가. ACTIVE 변형이 없으면 null.
 * @param soldOut ACTIVE 변형이 있으나 주문가능이 하나도 없음. ACTIVE 변형이 없으면 false.
 * @param variants 가격 오름차순 ACTIVE 변형 뷰.
 */
public record ProductView(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        @Nullable Money fromPrice,
        boolean soldOut,
        List<ProductVariantView> variants) {

    public ProductView {
        variants = List.copyOf(variants);
    }
}

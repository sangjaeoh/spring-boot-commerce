package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 상세 합성 뷰다. 상품 그룹 + ACTIVE 변형 + 재고 파생(주문가능·품절·대표가)을 싣는다.
 *
 * <p>대표가(fromPrice)는 ACTIVE 변형 최저가, 품절(soldOut)은 ACTIVE 변형이 있으나 주문가능이 하나도
 * 없을 때다. ACTIVE 변형이 없으면 fromPrice는 null·soldOut은 false다(제공할 것이 없음과 품절은 다르다).
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

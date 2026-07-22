package com.commerce.api.facade.view;

import com.commerce.product.entity.ProductStatus;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 상세 합성 뷰다.
 *
 * @param fromPrice ACTIVE 변형 최저가. ACTIVE 변형이 없으면 null.
 * @param soldOut ACTIVE 변형이 있으나 주문가능이 하나도 없음. ACTIVE 변형이 없으면 false.
 * @param variants 가격 오름차순 ACTIVE 변형 뷰.
 * @param imageUrls 정렬 순서대로의 이미지 URL. 첫 항목이 대표다.
 */
public record ProductView(
        UUID id,
        String name,
        @Nullable String description,
        ProductStatus status,
        @Nullable Money fromPrice,
        boolean soldOut,
        List<ProductVariantView> variants,
        List<String> imageUrls) {

    public ProductView {
        variants = List.copyOf(variants);
        imageUrls = List.copyOf(imageUrls);
    }
}

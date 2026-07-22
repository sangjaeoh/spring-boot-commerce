package com.commerce.product.info;

import com.commerce.product.entity.ProductImage;
import java.util.UUID;

/** 상품 이미지 조회 경계 모델이다. */
public record ProductImageInfo(UUID id, UUID productId, String url, int sortOrder) {

    /** 상품 이미지 엔티티에서 조회 모델을 만든다. */
    public static ProductImageInfo from(ProductImage image) {
        return new ProductImageInfo(image.getId(), image.getProductId(), image.getUrl(), image.getSortOrder());
    }
}

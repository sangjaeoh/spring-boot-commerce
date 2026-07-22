package com.commerce.product.application.provided;

import com.commerce.product.application.info.ProductImageInfo;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 상품 이미지 조회를 담당하는 서비스다. */
public interface ProductImageReader {

    /** 상품의 이미지 목록을 정렬 순서 오름차순으로 조회한다. 없으면 빈 목록이다. */
    List<ProductImageInfo> getByProductId(UUID productId);

    /** 여러 상품의 이미지 목록을 정렬 순서 오름차순으로 조회한다. 없으면 빈 목록이다. */
    List<ProductImageInfo> getByProductIds(Collection<UUID> productIds);
}

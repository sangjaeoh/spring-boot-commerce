package com.commerce.product.application.provided;

import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.domain.ProductVariantNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 상품 변형 조회를 담당하는 서비스다. */
public interface ProductVariantReader {

    /**
     * 변형을 조회한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     */
    ProductVariantInfo getVariant(UUID variantId);

    /** 주어진 ID들의 변형을 조회한다. */
    List<ProductVariantInfo> getVariants(Collection<UUID> variantIds);

    /** 같은 옵션 조합의 비-{@code RETIRED} 변형을 조회한다. */
    Optional<ProductVariantInfo> findNonRetiredByOptions(UUID productId, List<ProductOption> options);

    /** 상품의 변형 목록을 조회한다. 없으면 빈 목록이다. */
    List<ProductVariantInfo> getByProductId(UUID productId);

    /** 주어진 상품들의 변형 목록을 조회한다. 없으면 빈 목록이다. */
    List<ProductVariantInfo> getByProductIds(Collection<UUID> productIds);
}

package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.ProductImageInfo;
import com.commerce.domain.product.application.provided.ProductImageReader;
import com.commerce.domain.product.application.required.ProductImageRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductImageReader}의 기본 구현이다. */
@Service
class DefaultProductImageReader implements ProductImageReader {

    private final ProductImageRepository productImageRepository;

    DefaultProductImageReader(ProductImageRepository productImageRepository) {
        this.productImageRepository = productImageRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductImageInfo> getByProductId(UUID productId) {
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(ProductImageInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductImageInfo> getByProductIds(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productImageRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds).stream()
                .map(ProductImageInfo::from)
                .toList();
    }
}

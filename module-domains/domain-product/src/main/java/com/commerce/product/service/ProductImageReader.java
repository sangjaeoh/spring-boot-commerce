package com.commerce.product.service;

import com.commerce.product.info.ProductImageInfo;
import com.commerce.product.repository.ProductImageRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 이미지 조회를 담당하는 서비스다. */
@Service
public class ProductImageReader {

    private final ProductImageRepository productImageRepository;

    public ProductImageReader(ProductImageRepository productImageRepository) {
        this.productImageRepository = productImageRepository;
    }

    /** 상품의 이미지 목록을 정렬 순서 오름차순으로 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ProductImageInfo> getByProductId(UUID productId) {
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(ProductImageInfo::from)
                .toList();
    }

    /** 여러 상품의 이미지 목록을 정렬 순서 오름차순으로 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ProductImageInfo> getByProductIds(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productImageRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds).stream()
                .map(ProductImageInfo::from)
                .toList();
    }
}

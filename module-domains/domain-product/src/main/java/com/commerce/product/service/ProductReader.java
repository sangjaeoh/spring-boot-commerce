package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.repository.ProductRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 조회를 담당한다. */
@Service
public class ProductReader {

    private final ProductRepository productRepository;

    public ProductReader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 활성 상품을 조회한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    @Transactional(readOnly = true)
    public ProductInfo getProduct(UUID productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductInfo.from(product);
    }

    /** 주어진 ID들 중 활성 상품을 조회한다. */
    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Collection<UUID> productIds) {
        return productRepository.findByIdInAndDeletedAtIsNull(productIds).stream()
                .map(ProductInfo::from)
                .toList();
    }
}

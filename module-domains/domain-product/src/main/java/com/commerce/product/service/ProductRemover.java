package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.repository.ProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 논리삭제를 담당한다. */
@Service
public class ProductRemover {

    private final ProductRepository productRepository;

    public ProductRemover(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 상품을 논리삭제한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    @Transactional
    public void delete(UUID productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.delete();
    }
}

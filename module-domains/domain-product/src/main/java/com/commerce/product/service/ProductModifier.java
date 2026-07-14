package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.repository.ProductRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 노출 전환·상품명·설명 변경을 담당한다. */
@Service
public class ProductModifier {

    private final ProductRepository productRepository;

    public ProductModifier(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** 상품을 노출한다. */
    @Transactional
    public void show(UUID productId) {
        find(productId).show();
    }

    /** 상품을 숨긴다. */
    @Transactional
    public void hide(UUID productId) {
        find(productId).hide();
    }

    /** 상품명을 바꾼다. */
    @Transactional
    public void rename(UUID productId, String newName) {
        find(productId).rename(newName);
    }

    /** 상세 설명을 바꾼다. */
    @Transactional
    public void changeDescription(UUID productId, @Nullable String newDescription) {
        find(productId).changeDescription(newDescription);
    }

    private Product find(UUID productId) {
        return productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}

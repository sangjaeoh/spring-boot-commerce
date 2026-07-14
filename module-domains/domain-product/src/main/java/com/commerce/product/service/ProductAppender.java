package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.repository.ProductRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 등록을 담당한다. */
@Service
public class ProductAppender {

    private final ProductRepository productRepository;

    public ProductAppender(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** 상품을 숨김 상태로 등록하고 새 상품 ID를 반환한다. */
    @Transactional
    public UUID register(String name, @Nullable String description) {
        return productRepository.save(Product.create(name, description)).getId();
    }
}

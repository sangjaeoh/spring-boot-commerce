package com.commerce.product.service;

import com.commerce.product.entity.Product;
import com.commerce.product.exception.CategoryNotFoundException;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.repository.CategoryRepository;
import com.commerce.product.repository.ProductRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 등록을 담당하는 서비스다. */
@Service
public class ProductAppender {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductAppender(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 상품을 숨김 상태로 등록하고 새 상품 ID를 반환한다. 카테고리가 있으면 분류를 지정한다.
     *
     * @throws CategoryNotFoundException 지정한 활성 카테고리가 없으면
     */
    @Transactional
    public UUID register(String name, @Nullable String description, @Nullable UUID categoryId) {
        if (categoryId != null && !categoryRepository.existsByIdAndDeletedAtIsNull(categoryId)) {
            throw new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        Product product = Product.create(name, description);
        product.assignCategory(categoryId);
        return productRepository.save(product).getId();
    }
}

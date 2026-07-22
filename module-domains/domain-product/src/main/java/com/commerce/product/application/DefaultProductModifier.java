package com.commerce.product.application;

import com.commerce.product.application.provided.ProductModifier;
import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductNotFoundException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductModifier}의 기본 구현이다. */
@Service
class DefaultProductModifier implements ProductModifier {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    DefaultProductModifier(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @Override
    public void assignCategory(UUID productId, @Nullable UUID categoryId) {
        if (categoryId != null && !categoryRepository.existsByIdAndDeletedAtIsNull(categoryId)) {
            throw new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        find(productId).assignCategory(categoryId);
    }

    @Transactional
    @Override
    public void show(UUID productId) {
        find(productId).show();
    }

    @Transactional
    @Override
    public void hide(UUID productId) {
        find(productId).hide();
    }

    @Transactional
    @Override
    public void rename(UUID productId, String newName) {
        find(productId).rename(newName);
    }

    @Transactional
    @Override
    public void changeDescription(UUID productId, @Nullable String newDescription) {
        find(productId).changeDescription(newDescription);
    }

    /** 활성 상품을 찾고 없으면 거부한다. */
    private Product find(UUID productId) {
        return productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}

package com.commerce.product.application;

import com.commerce.product.application.provided.ProductAppender;
import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.exception.CategoryNotFoundException;
import com.commerce.product.domain.exception.ProductErrorCode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductAppender}의 기본 구현이다. */
@Service
class DefaultProductAppender implements ProductAppender {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    DefaultProductAppender(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @Override
    public UUID register(String name, @Nullable String description, @Nullable UUID categoryId) {
        if (categoryId != null && !categoryRepository.existsByIdAndDeletedAtIsNull(categoryId)) {
            throw new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        Product product = Product.create(name, description);
        product.assignCategory(categoryId);
        return productRepository.save(product).getId();
    }
}

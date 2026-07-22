package com.commerce.product.application;

import com.commerce.product.application.provided.CategoryModifier;
import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.ProductErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryModifier}의 기본 구현이다. */
@Service
class DefaultCategoryModifier implements CategoryModifier {

    private final CategoryRepository categoryRepository;

    DefaultCategoryModifier(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @Override
    public void rename(UUID categoryId, String newName) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .rename(newName);
    }
}

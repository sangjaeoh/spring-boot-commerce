package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.provided.CategoryModifier;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.exception.CategoryNotFoundException;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryModifier}의 기본 구현이다. */
@Service
class DefaultCategoryModifier implements CategoryModifier {

    private final CategoryRepository categoryRepository;

    DefaultCategoryModifier(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @CacheEvict(cacheNames = CategoryCacheNames.CATEGORY, allEntries = true)
    @Transactional
    @Override
    public void rename(UUID categoryId, String newName) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .rename(newName);
    }
}

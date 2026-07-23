package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.CategoryRemover;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.exception.CategoryNotFoundException;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryRemover}의 기본 구현이다. */
@Service
class DefaultCategoryRemover implements CategoryRemover {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    DefaultCategoryRemover(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void delete(UUID categoryId) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .delete(clock.instant());
    }
}

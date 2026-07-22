package com.commerce.product.application;

import com.commerce.product.application.provided.CategoryAppender;
import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.domain.Category;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryAppender}의 기본 구현이다. */
@Service
class DefaultCategoryAppender implements CategoryAppender {

    private final CategoryRepository categoryRepository;

    DefaultCategoryAppender(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @Override
    public UUID create(String name) {
        return categoryRepository.save(Category.create(name)).getId();
    }
}

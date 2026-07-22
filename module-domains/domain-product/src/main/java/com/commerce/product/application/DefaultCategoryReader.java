package com.commerce.product.application;

import com.commerce.product.application.info.CategoryInfo;
import com.commerce.product.application.provided.CategoryReader;
import com.commerce.product.application.required.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryReader}의 기본 구현이다. */
@Service
class DefaultCategoryReader implements CategoryReader {

    private final CategoryRepository categoryRepository;

    DefaultCategoryReader(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryInfo> getCategories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryInfo::from)
                .toList();
    }
}

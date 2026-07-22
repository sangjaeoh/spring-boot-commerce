package com.commerce.product.application;

import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.ProductErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 이름 변경을 담당하는 서비스다. */
@Service
public class CategoryModifier {

    private final CategoryRepository categoryRepository;

    public CategoryModifier(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 카테고리 이름을 바꾼다.
     *
     * @throws CategoryNotFoundException 활성 카테고리가 없으면
     */
    @Transactional
    public void rename(UUID categoryId, String newName) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .rename(newName);
    }
}

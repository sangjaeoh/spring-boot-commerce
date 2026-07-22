package com.commerce.product.application;

import com.commerce.product.application.required.CategoryRepository;
import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.ProductErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 논리삭제를 담당하는 서비스다. */
@Service
public class CategoryRemover {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CategoryRemover(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    /**
     * 카테고리를 논리삭제한다. 이 카테고리를 참조하는 상품은 재지정 전까지 기존 ID를 유지한다.
     *
     * @throws CategoryNotFoundException 활성 카테고리가 없으면
     */
    @Transactional
    public void delete(UUID categoryId) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .delete(clock.instant());
    }
}

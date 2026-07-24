package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.required.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DefaultCategoryReader}의 캐시 미스 경로 위임 서비스다.
 *
 * <p>provided 계약을 만들지 않는다 — 컨텍스트 안에 {@code CategoryReader} 구현체가
 * {@link DefaultCategoryReader} 하나만 존재하도록 유지한다.
 */
@Service
class TransactionalCategoryReader {

    private final CategoryRepository categoryRepository;

    TransactionalCategoryReader(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    List<CategoryInfo> getCategories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryInfo::from)
                .toList();
    }
}

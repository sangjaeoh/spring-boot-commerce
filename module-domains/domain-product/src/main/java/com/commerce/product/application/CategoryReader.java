package com.commerce.product.application;

import com.commerce.product.application.info.CategoryInfo;
import com.commerce.product.application.required.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 조회를 담당하는 서비스다. */
@Service
public class CategoryReader {

    private final CategoryRepository categoryRepository;

    public CategoryReader(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 활성 카테고리 목록을 이름순으로 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<CategoryInfo> getCategories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryInfo::from)
                .toList();
    }
}

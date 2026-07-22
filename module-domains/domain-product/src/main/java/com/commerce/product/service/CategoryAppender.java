package com.commerce.product.service;

import com.commerce.product.entity.Category;
import com.commerce.product.repository.CategoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 생성을 담당하는 서비스다. */
@Service
public class CategoryAppender {

    private final CategoryRepository categoryRepository;

    public CategoryAppender(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 카테고리를 생성하고 새 카테고리 ID를 반환한다. */
    @Transactional
    public UUID create(String name) {
        return categoryRepository.save(Category.create(name)).getId();
    }
}

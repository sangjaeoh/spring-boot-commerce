package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.provided.CategoryReader;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** {@link CategoryReader}의 기본 구현이다. */
@Service
class DefaultCategoryReader implements CategoryReader {

    private final TransactionalCategoryReader transactionalCategoryReader;

    DefaultCategoryReader(TransactionalCategoryReader transactionalCategoryReader) {
        this.transactionalCategoryReader = transactionalCategoryReader;
    }

    @Cacheable(cacheNames = CategoryCacheNames.CATEGORY, key = "'all'")
    @Override
    public List<CategoryInfo> getCategories() {
        return transactionalCategoryReader.getCategories();
    }
}

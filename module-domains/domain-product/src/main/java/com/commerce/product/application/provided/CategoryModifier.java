package com.commerce.product.application.provided;

import com.commerce.product.domain.CategoryNotFoundException;
import java.util.UUID;

/** 카테고리 이름 변경을 담당하는 서비스다. */
public interface CategoryModifier {

    /**
     * 카테고리 이름을 바꾼다.
     *
     * @throws CategoryNotFoundException 활성 카테고리가 없으면
     */
    void rename(UUID categoryId, String newName);
}

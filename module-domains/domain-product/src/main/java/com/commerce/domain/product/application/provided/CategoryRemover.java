package com.commerce.domain.product.application.provided;

import com.commerce.domain.product.domain.exception.CategoryNotFoundException;
import java.util.UUID;

/** 카테고리 논리삭제를 담당하는 서비스다. */
public interface CategoryRemover {

    /**
     * 카테고리를 논리삭제한다. 이 카테고리를 참조하는 상품은 재지정 전까지 기존 ID를 유지한다.
     *
     * @throws CategoryNotFoundException 활성 카테고리가 없으면
     */
    void delete(UUID categoryId);
}

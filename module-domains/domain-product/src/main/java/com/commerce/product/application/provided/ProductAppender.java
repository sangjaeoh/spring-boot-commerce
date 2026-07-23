package com.commerce.product.application.provided;

import com.commerce.product.domain.exception.CategoryNotFoundException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 등록을 담당하는 서비스다. */
public interface ProductAppender {

    /**
     * 상품을 숨김 상태로 등록하고 새 상품 ID를 반환한다. 카테고리가 있으면 분류를 지정한다.
     *
     * @throws CategoryNotFoundException 지정한 활성 카테고리가 없으면
     */
    UUID register(String name, @Nullable String description, @Nullable UUID categoryId);
}

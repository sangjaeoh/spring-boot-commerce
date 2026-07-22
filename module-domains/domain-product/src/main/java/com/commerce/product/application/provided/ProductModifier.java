package com.commerce.product.application.provided;

import com.commerce.product.domain.CategoryNotFoundException;
import com.commerce.product.domain.ProductNotFoundException;
import com.commerce.product.domain.ProductStatusException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 노출 전환·상품명·설명·분류 변경을 담당하는 서비스다. */
public interface ProductModifier {

    /**
     * 카테고리를 지정한다. null이면 미분류로 해제한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws CategoryNotFoundException 지정한 활성 카테고리가 없으면
     */
    void assignCategory(UUID productId, @Nullable UUID categoryId);

    /**
     * 상품을 노출한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws ProductStatusException 숨김 상태가 아니면
     */
    void show(UUID productId);

    /**
     * 상품을 숨긴다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws ProductStatusException 노출 상태가 아니면
     */
    void hide(UUID productId);

    /**
     * 상품명을 바꾼다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    void rename(UUID productId, String newName);

    /**
     * 상세 설명을 바꾼다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    void changeDescription(UUID productId, @Nullable String newDescription);
}

package com.commerce.product.application.provided;

import com.commerce.product.domain.exception.ProductNotFoundException;
import java.util.UUID;

/** 상품 논리삭제를 담당하는 서비스다. */
public interface ProductRemover {

    /**
     * 상품을 논리삭제한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     */
    void delete(UUID productId);
}

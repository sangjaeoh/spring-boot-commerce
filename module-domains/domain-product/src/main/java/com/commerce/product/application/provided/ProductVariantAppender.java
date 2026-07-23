package com.commerce.product.application.provided;

import com.commerce.product.domain.ProductOption;
import com.commerce.product.domain.exception.DuplicateVariantOptionException;
import com.commerce.product.domain.exception.InvalidVariantException;
import com.commerce.product.domain.exception.ProductNotFoundException;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;

/** 상품 변형 생성을 담당하는 서비스다. */
public interface ProductVariantAppender {

    /**
     * 변형을 비활성 상태로 생성하고 새 변형 ID를 반환한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws DuplicateVariantOptionException 비-{@code RETIRED} 변형과 옵션 조합이 겹칠 때
     * @throws InvalidVariantException 옵션이 올바르지 않거나 판매가가 최소가(1원) 미만이면
     */
    UUID create(UUID productId, Money price, List<ProductOption> options);
}

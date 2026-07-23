package com.commerce.product.application.provided;

import com.commerce.product.domain.exception.InvalidVariantException;
import com.commerce.product.domain.exception.ProductVariantNotFoundException;
import com.commerce.product.domain.exception.ProductVariantStatusException;
import com.commerce.shared.entity.Money;
import java.util.UUID;

/** 상품 변형 상태 전이·가격 변경을 담당하는 서비스다. */
public interface ProductVariantModifier {

    /**
     * 변형을 판매 제공한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 비활성 상태가 아니면
     */
    void enable(UUID variantId);

    /**
     * 변형 판매 제공을 중단한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 판매 제공 상태가 아니면
     */
    void disable(UUID variantId);

    /**
     * 변형을 은퇴시킨다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 이미 은퇴한 변형이면
     */
    void retire(UUID variantId);

    /**
     * 변형 판매가를 바꾼다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 은퇴한 변형이면
     * @throws InvalidVariantException 판매가가 최소가(1원) 미만이면
     */
    void changePrice(UUID variantId, Money newPrice);
}

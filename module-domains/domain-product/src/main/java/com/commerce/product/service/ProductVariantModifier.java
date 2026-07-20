package com.commerce.product.service;

import com.commerce.product.entity.ProductVariant;
import com.commerce.product.exception.InvalidVariantException;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductVariantNotFoundException;
import com.commerce.product.exception.ProductVariantStatusException;
import com.commerce.product.repository.ProductVariantRepository;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 변형 상태 전이·가격 변경을 담당하는 서비스다. */
@Service
public class ProductVariantModifier {

    private final ProductVariantRepository variantRepository;

    public ProductVariantModifier(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    /**
     * 변형을 판매 제공한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 비활성 상태가 아니면
     */
    @Transactional
    public void enable(UUID variantId) {
        find(variantId).enable();
    }

    /**
     * 변형 판매 제공을 중단한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 판매 제공 상태가 아니면
     */
    @Transactional
    public void disable(UUID variantId) {
        find(variantId).disable();
    }

    /**
     * 변형을 은퇴시킨다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 이미 은퇴한 변형이면
     */
    @Transactional
    public void retire(UUID variantId) {
        find(variantId).retire();
    }

    /**
     * 변형 판매가를 바꾼다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     * @throws ProductVariantStatusException 은퇴한 변형이면
     * @throws InvalidVariantException 판매가가 최소가(1원) 미만이면
     */
    @Transactional
    public void changePrice(UUID variantId, Money newPrice) {
        find(variantId).changePrice(newPrice);
    }

    /** 변형을 찾고 없으면 거부한다. */
    private ProductVariant find(UUID variantId) {
        return variantRepository
                .findById(variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(ProductErrorCode.VARIANT_NOT_FOUND));
    }
}

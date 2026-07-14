package com.commerce.product.service;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductVariant;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductVariantNotFoundException;
import com.commerce.product.repository.ProductVariantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 변형 상태 전이·가격 변경을 담당한다. */
@Service
public class ProductVariantModifier {

    private final ProductVariantRepository variantRepository;

    public ProductVariantModifier(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    /** 변형을 판매 제공한다. */
    @Transactional
    public void enable(UUID variantId) {
        find(variantId).enable();
    }

    /** 변형 판매 제공을 중단한다. */
    @Transactional
    public void disable(UUID variantId) {
        find(variantId).disable();
    }

    /** 변형을 은퇴시킨다. */
    @Transactional
    public void retire(UUID variantId) {
        find(variantId).retire();
    }

    /** 변형 판매가를 바꾼다. */
    @Transactional
    public void changePrice(UUID variantId, Money newPrice) {
        find(variantId).changePrice(newPrice);
    }

    private ProductVariant find(UUID variantId) {
        return variantRepository
                .findById(variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(ProductErrorCode.VARIANT_NOT_FOUND));
    }
}

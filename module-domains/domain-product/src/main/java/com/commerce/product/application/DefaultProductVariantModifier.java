package com.commerce.product.application;

import com.commerce.product.application.provided.ProductVariantModifier;
import com.commerce.product.application.required.ProductVariantRepository;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.exception.ProductErrorCode;
import com.commerce.product.domain.exception.ProductVariantNotFoundException;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductVariantModifier}의 기본 구현이다. */
@Service
class DefaultProductVariantModifier implements ProductVariantModifier {

    private final ProductVariantRepository variantRepository;

    DefaultProductVariantModifier(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    @Transactional
    @Override
    public void enable(UUID variantId) {
        find(variantId).enable();
    }

    @Transactional
    @Override
    public void disable(UUID variantId) {
        find(variantId).disable();
    }

    @Transactional
    @Override
    public void retire(UUID variantId) {
        find(variantId).retire();
    }

    @Transactional
    @Override
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

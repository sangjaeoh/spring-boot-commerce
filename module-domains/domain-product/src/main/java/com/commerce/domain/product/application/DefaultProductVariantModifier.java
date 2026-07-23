package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.ProductVariantModifier;
import com.commerce.domain.product.application.required.ProductVariantRepository;
import com.commerce.domain.product.domain.ProductVariant;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import com.commerce.domain.product.domain.exception.ProductVariantNotFoundException;
import com.commerce.domain.shared.entity.Money;
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

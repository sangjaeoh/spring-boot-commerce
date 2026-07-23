package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.ProductVariantInfo;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.product.application.required.ProductVariantRepository;
import com.commerce.domain.product.domain.NormalizedOptions;
import com.commerce.domain.product.domain.ProductOption;
import com.commerce.domain.product.domain.ProductVariant;
import com.commerce.domain.product.domain.ProductVariantStatus;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import com.commerce.domain.product.domain.exception.ProductVariantNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductVariantReader}의 기본 구현이다. */
@Service
class DefaultProductVariantReader implements ProductVariantReader {

    private final ProductVariantRepository variantRepository;

    DefaultProductVariantReader(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public ProductVariantInfo getVariant(UUID variantId) {
        ProductVariant variant = variantRepository
                .findById(variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(ProductErrorCode.VARIANT_NOT_FOUND));
        return ProductVariantInfo.from(variant);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductVariantInfo> getVariants(Collection<UUID> variantIds) {
        return variantRepository.findAllById(variantIds).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProductVariantInfo> findNonRetiredByOptions(UUID productId, List<ProductOption> options) {
        String signature = NormalizedOptions.of(options).signature();
        return variantRepository
                .findByProductIdAndOptionSignatureAndStatusNot(productId, signature, ProductVariantStatus.RETIRED)
                .map(ProductVariantInfo::from);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductVariantInfo> getByProductId(UUID productId) {
        return variantRepository.findByProductId(productId).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductVariantInfo> getByProductIds(Collection<UUID> productIds) {
        return variantRepository.findByProductIdIn(productIds).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }
}

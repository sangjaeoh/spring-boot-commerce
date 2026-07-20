package com.commerce.product.service;

import com.commerce.product.entity.NormalizedOptions;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.entity.ProductVariant;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductVariantNotFoundException;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.repository.ProductVariantRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 변형 조회를 담당하는 서비스다. */
@Service
public class ProductVariantReader {

    private final ProductVariantRepository variantRepository;

    public ProductVariantReader(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    /**
     * 변형을 조회한다.
     *
     * @throws ProductVariantNotFoundException 변형이 없으면
     */
    @Transactional(readOnly = true)
    public ProductVariantInfo getVariant(UUID variantId) {
        ProductVariant variant = variantRepository
                .findById(variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(ProductErrorCode.VARIANT_NOT_FOUND));
        return ProductVariantInfo.from(variant);
    }

    /** 주어진 ID들의 변형을 조회한다. */
    @Transactional(readOnly = true)
    public List<ProductVariantInfo> getVariants(Collection<UUID> variantIds) {
        return variantRepository.findAllById(variantIds).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }

    /** 같은 옵션 조합의 비-{@code RETIRED} 변형을 조회한다. 중복 검사와 같은 시그니처 정규화를 쓴다. */
    @Transactional(readOnly = true)
    public Optional<ProductVariantInfo> findNonRetiredByOptions(UUID productId, List<ProductOption> options) {
        String signature = NormalizedOptions.of(options).signature();
        return variantRepository
                .findByProductIdAndOptionSignatureAndStatusNot(productId, signature, ProductVariantStatus.RETIRED)
                .map(ProductVariantInfo::from);
    }

    /** 상품의 변형 목록을 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ProductVariantInfo> getByProductId(UUID productId) {
        return variantRepository.findByProductId(productId).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }

    /** 주어진 상품들의 변형 목록을 조회한다. 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<ProductVariantInfo> getByProductIds(Collection<UUID> productIds) {
        return variantRepository.findByProductIdIn(productIds).stream()
                .map(ProductVariantInfo::from)
                .toList();
    }
}

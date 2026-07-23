package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.ProductVariantAppender;
import com.commerce.domain.product.application.required.ProductRepository;
import com.commerce.domain.product.application.required.ProductVariantRepository;
import com.commerce.domain.product.domain.NormalizedOptions;
import com.commerce.domain.product.domain.ProductOption;
import com.commerce.domain.product.domain.ProductVariant;
import com.commerce.domain.product.domain.ProductVariantStatus;
import com.commerce.domain.product.domain.exception.DuplicateVariantOptionException;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import com.commerce.domain.product.domain.exception.ProductNotFoundException;
import com.commerce.domain.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductVariantAppender}의 기본 구현이다. */
@Service
class DefaultProductVariantAppender implements ProductVariantAppender {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    DefaultProductVariantAppender(ProductRepository productRepository, ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    @Override
    public UUID create(UUID productId, Money price, List<ProductOption> options) {
        if (!productRepository.existsByIdAndDeletedAtIsNull(productId)) {
            throw new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        NormalizedOptions normalized = NormalizedOptions.of(options);
        if (variantRepository.existsByProductIdAndOptionSignatureAndStatusNot(
                productId, normalized.signature(), ProductVariantStatus.RETIRED)) {
            throw new DuplicateVariantOptionException(ProductErrorCode.DUPLICATE_VARIANT_OPTION);
        }
        try {
            return variantRepository
                    .saveAndFlush(ProductVariant.create(productId, price, normalized))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 생성 경합 방어
            throw new DuplicateVariantOptionException(ProductErrorCode.DUPLICATE_VARIANT_OPTION);
        }
    }
}

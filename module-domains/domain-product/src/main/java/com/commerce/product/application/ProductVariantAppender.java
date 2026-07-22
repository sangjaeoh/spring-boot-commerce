package com.commerce.product.application;

import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.application.required.ProductVariantRepository;
import com.commerce.product.domain.DuplicateVariantOptionException;
import com.commerce.product.domain.InvalidVariantException;
import com.commerce.product.domain.NormalizedOptions;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductNotFoundException;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.ProductVariantStatus;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 변형 생성을 담당하는 서비스다. */
@Service
public class ProductVariantAppender {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ProductVariantAppender(ProductRepository productRepository, ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * 변형을 비활성 상태로 생성하고 새 변형 ID를 반환한다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws DuplicateVariantOptionException 비-{@code RETIRED} 변형과 옵션 조합이 겹칠 때
     * @throws InvalidVariantException 옵션이 올바르지 않거나 판매가가 최소가(1원) 미만이면
     */
    @Transactional
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

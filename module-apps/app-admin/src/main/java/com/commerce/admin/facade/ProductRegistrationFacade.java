package com.commerce.admin.facade;

import com.commerce.product.application.provided.ProductAppender;
import com.commerce.product.application.provided.ProductModifier;
import com.commerce.product.application.provided.ProductVariantAppender;
import com.commerce.product.application.provided.ProductVariantModifier;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.domain.ProductVariantStatus;
import com.commerce.product.domain.exception.CategoryNotFoundException;
import com.commerce.product.domain.exception.DuplicateVariantOptionException;
import com.commerce.product.domain.exception.InvalidVariantException;
import com.commerce.product.domain.exception.ProductNotFoundException;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockAppender;
import com.commerce.stock.application.provided.StockReader;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** 상품 등록과 변형·초기 재고 시딩을 조율하는 파사드다. */
@Component
public class ProductRegistrationFacade {

    private final ProductAppender productAppender;
    private final ProductVariantAppender variantAppender;
    private final ProductVariantReader variantReader;
    private final StockAppender stockAppender;
    private final StockReader stockReader;
    private final ProductVariantModifier variantModifier;
    private final ProductModifier productModifier;

    public ProductRegistrationFacade(
            ProductAppender productAppender,
            ProductVariantAppender variantAppender,
            ProductVariantReader variantReader,
            StockAppender stockAppender,
            StockReader stockReader,
            ProductVariantModifier variantModifier,
            ProductModifier productModifier) {
        this.productAppender = productAppender;
        this.variantAppender = variantAppender;
        this.variantReader = variantReader;
        this.stockAppender = stockAppender;
        this.stockReader = stockReader;
        this.variantModifier = variantModifier;
        this.productModifier = productModifier;
    }

    /**
     * 상품·첫 변형·재고를 시딩하고 판매를 시작한다. 카테고리가 있으면 분류를 지정한다.
     *
     * @throws CategoryNotFoundException 지정한 활성 카테고리가 없으면
     * @throws InvalidVariantException 옵션이 올바르지 않거나 판매가가 최소가 미만이면
     */
    public UUID registerProduct(
            String name,
            @Nullable String description,
            @Nullable UUID categoryId,
            Money price,
            List<ProductOption> options,
            int initialQuantity) {
        // 1. 상품 등록(HIDDEN)
        UUID productId = productAppender.register(name, description, categoryId);
        // 2. 첫 변형·재고 시딩
        addVariant(productId, price, options, initialQuantity);
        // 3. 판매 시작(ON_SALE)
        productModifier.show(productId);
        return productId;
    }

    /** 분류 없이 상품·첫 변형·재고를 시딩하고 판매를 시작한다. */
    public UUID registerProduct(
            String name, @Nullable String description, Money price, List<ProductOption> options, int initialQuantity) {
        return registerProduct(name, description, null, price, options, initialQuantity);
    }

    /**
     * 기존 상품에 변형·재고를 시딩하고 변형 ID를 반환한다.
     *
     * <p>같은 옵션 조합의 DISABLED 변형이 있으면 남은 단계를 재개한다. 재개는 기존 변형의 가격을 유지하고
     * 재고가 이미 있으면 초기수량을 쓰지 않으며, 관리자가 비활성화한 동일 옵션 변형도 같은 경로로
     * 재활성화된다. 완결(ACTIVE) 변형은 중복으로 거부된다.
     *
     * @throws ProductNotFoundException 활성 상품이 없으면
     * @throws DuplicateVariantOptionException 완결(ACTIVE) 변형과 옵션 조합이 겹치면
     * @throws InvalidVariantException 옵션이 올바르지 않거나 판매가가 최소가 미만이면
     */
    public UUID addVariant(UUID productId, Money price, List<ProductOption> options, int initialQuantity) {
        return variantReader
                .findNonRetiredByOptions(productId, options)
                .filter(variant -> variant.status() == ProductVariantStatus.DISABLED)
                .map(variant -> resumeSeeding(variant.id(), initialQuantity))
                .orElseGet(() -> seedVariant(productId, price, options, initialQuantity));
    }

    /** 새 변형과 초기 재고를 만들고 변형을 활성화한다. */
    private UUID seedVariant(UUID productId, Money price, List<ProductOption> options, int initialQuantity) {
        // 1. 변형 생성(DISABLED)
        UUID variantId = variantAppender.create(productId, price, options);
        // 2. 초기 재고 생성
        stockAppender.create(variantId, initialQuantity);
        // 3. 변형 활성화(ACTIVE)
        variantModifier.enable(variantId);
        return variantId;
    }

    /** 중단된 시딩의 남은 단계를 이어간다. */
    private UUID resumeSeeding(UUID variantId, int initialQuantity) {
        // 1. 재고가 없으면 초기 재고 생성
        if (!stockReader.existsByVariantId(variantId)) {
            stockAppender.create(variantId, initialQuantity);
        }
        // 2. 변형 활성화
        variantModifier.enable(variantId);
        return variantId;
    }
}

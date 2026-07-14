package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.service.ProductAppender;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.stock.service.StockAppender;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 상품 등록과 첫 변형·초기 재고 시딩을 조율하고 상품 ID를 반환한다.
 *
 * <p>순서는 상품 {@code register}(HIDDEN) → 변형 {@code create}(DISABLED) → 재고 {@code create} →
 * 변형 {@code enable} → 상품 {@code show}(ON_SALE)다. 변형을 DISABLED로 먼저 둬 재고 없는 ACTIVE 변형
 * 창을 없앤다. 파괴적 보상은 없다 — 실패 시 남은 부분 상태에서 재시도로 재개한다.
 */
@Component
public class ProductRegistrationFacade {

    private final ProductAppender productAppender;
    private final ProductVariantAppender variantAppender;
    private final StockAppender stockAppender;
    private final ProductVariantModifier variantModifier;
    private final ProductModifier productModifier;

    public ProductRegistrationFacade(
            ProductAppender productAppender,
            ProductVariantAppender variantAppender,
            StockAppender stockAppender,
            ProductVariantModifier variantModifier,
            ProductModifier productModifier) {
        this.productAppender = productAppender;
        this.variantAppender = variantAppender;
        this.stockAppender = stockAppender;
        this.variantModifier = variantModifier;
        this.productModifier = productModifier;
    }

    /** 상품·첫 변형·재고를 시딩하고 판매를 시작한다. */
    public UUID registerProduct(
            String name, @Nullable String description, Money price, List<ProductOption> options, int initialQuantity) {
        UUID productId = productAppender.register(name, description);
        UUID variantId = variantAppender.create(productId, price, options);
        stockAppender.create(variantId, initialQuantity);
        variantModifier.enable(variantId);
        productModifier.show(productId);
        return productId;
    }
}

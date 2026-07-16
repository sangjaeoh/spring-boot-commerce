package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductOption;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.service.ProductAppender;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductVariantAppender;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockAppender;
import com.commerce.stock.service.StockReader;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 상품 등록과 변형·초기 재고 시딩을 조율한다.
 *
 * <p>순서는 상품 {@code register}(HIDDEN) → 변형 {@code create}(DISABLED) → 재고 {@code create} →
 * 변형 {@code enable} → 상품 {@code show}(ON_SALE)다. 기존 상품의 추가 변형은 변형 {@code create}부터
 * 같은 순서를 따른다. 변형을 DISABLED로 먼저 둬 재고 없는 ACTIVE 변형 창을 없앤다. 파괴적 보상은
 * 없다 — 실패 시 남은 부분 상태에서 재시도로 재개한다(같은 옵션의 DISABLED 변형이 있으면 재생성 대신
 * 남은 단계를 이어간다).
 */
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

    /** 상품·첫 변형·재고를 시딩하고 판매를 시작한다. */
    public UUID registerProduct(
            String name, @Nullable String description, Money price, List<ProductOption> options, int initialQuantity) {
        UUID productId = productAppender.register(name, description);
        addVariant(productId, price, options, initialQuantity);
        productModifier.show(productId);
        return productId;
    }

    /**
     * 기존 상품에 변형·재고를 시딩하고 변형 ID를 반환한다.
     *
     * <p>같은 옵션 조합의 DISABLED 변형이 있으면 중단된 시딩으로 보고 남은 단계(재고 존재 확인 →
     * 재고 {@code create} → {@code enable})를 재개한다 — 재시도가 중복 검사에 막혀 영구 차단되지
     * 않는다. 재개는 기존 변형의 가격을 유지하고 재고가 이미 있으면 초기수량을 쓰지 않는다. 관리자가
     * 비활성화한 동일 옵션 변형도 같은 경로로 재활성화된다(재개 우선 — 시딩 중단과 구분할 증거가 없다).
     * 완결(ACTIVE) 변형은 기존과 같이 중복으로 거부된다.
     */
    public UUID addVariant(UUID productId, Money price, List<ProductOption> options, int initialQuantity) {
        return variantReader
                .findNonRetiredByOptions(productId, options)
                .filter(variant -> variant.status() == ProductVariantStatus.DISABLED)
                .map(variant -> resumeSeeding(variant.id(), initialQuantity))
                .orElseGet(() -> seedVariant(productId, price, options, initialQuantity));
    }

    private UUID seedVariant(UUID productId, Money price, List<ProductOption> options, int initialQuantity) {
        UUID variantId = variantAppender.create(productId, price, options);
        stockAppender.create(variantId, initialQuantity);
        variantModifier.enable(variantId);
        return variantId;
    }

    /** 중단된 시딩의 남은 단계를 이어간다 — 재고가 없으면 만들고 변형을 활성화한다. */
    private UUID resumeSeeding(UUID variantId, int initialQuantity) {
        if (!stockReader.existsByVariantId(variantId)) {
            stockAppender.create(variantId, initialQuantity);
        }
        variantModifier.enable(variantId);
        return variantId;
    }
}

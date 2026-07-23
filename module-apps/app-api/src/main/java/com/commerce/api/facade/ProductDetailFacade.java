package com.commerce.api.facade;

import com.commerce.api.facade.view.ProductVariantView;
import com.commerce.api.facade.view.ProductView;
import com.commerce.product.application.info.ProductImageInfo;
import com.commerce.product.application.info.ProductInfo;
import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.application.provided.ProductImageReader;
import com.commerce.product.application.provided.ProductReader;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.product.domain.ProductVariantStatus;
import com.commerce.product.domain.exception.ProductNotFoundException;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.info.StockInfo;
import com.commerce.stock.application.provided.StockReader;
import com.commerce.stock.domain.StockStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 상품 상세 조회를 ACTIVE 변형·재고와 합성해 주문가능(orderable)·품절·대표가를 파생하는 파사드다. */
@Component
public class ProductDetailFacade {

    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final StockReader stockReader;
    private final ProductImageReader imageReader;

    public ProductDetailFacade(
            ProductReader productReader,
            ProductVariantReader variantReader,
            StockReader stockReader,
            ProductImageReader imageReader) {
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.stockReader = stockReader;
        this.imageReader = imageReader;
    }

    /**
     * 상품 상세를 합성해 반환한다. 변형은 가격 오름차순으로 싣는다.
     *
     * @throws ProductNotFoundException 노출 상품이 없으면(미존재·숨김·삭제 포함)
     */
    public ProductView getProductDetail(UUID productId) {
        // 1. 노출 상품 조회
        ProductInfo product = productReader.getExposedProduct(productId);
        // 2. ACTIVE 변형과 재고 기준 주문가능 수집
        List<ProductVariantInfo> activeVariants = variantReader.getByProductId(productId).stream()
                .filter(variant -> variant.status() == ProductVariantStatus.ACTIVE)
                .toList();
        Set<UUID> orderableVariantIds = orderableVariantIds(activeVariants);
        // 3. 변형 뷰 조립 — 가격 오름차순, 대표가·품절 파생
        List<ProductVariantView> variants = new ArrayList<>();
        Money fromPrice = null;
        boolean anyOrderable = false;
        for (ProductVariantInfo variant : activeVariants) {
            boolean orderable = orderableVariantIds.contains(variant.id());
            variants.add(new ProductVariantView(variant.id(), variant.optionLabel(), variant.price(), orderable));
            fromPrice = fromPrice == null ? variant.price() : min(fromPrice, variant.price());
            anyOrderable = anyOrderable || orderable;
        }
        variants.sort(Comparator.comparingLong(
                        (ProductVariantView view) -> view.price().amount())
                .thenComparing(ProductVariantView::variantId));
        boolean soldOut = !variants.isEmpty() && !anyOrderable;
        // 4. 이미지 URL — 정렬 순서대로, 첫 항목이 대표
        List<String> imageUrls = imageReader.getByProductId(productId).stream()
                .map(ProductImageInfo::url)
                .toList();
        return new ProductView(
                product.id(),
                product.name(),
                product.description(),
                product.status(),
                fromPrice,
                soldOut,
                variants,
                imageUrls);
    }

    /** 재고가 받쳐주는(SELLABLE ∧ 수량 1 이상) 변형 ID를 모은다. */
    private Set<UUID> orderableVariantIds(List<ProductVariantInfo> activeVariants) {
        if (activeVariants.isEmpty()) {
            return Set.of();
        }
        List<UUID> variantIds =
                activeVariants.stream().map(ProductVariantInfo::id).toList();
        return stockReader.getByVariantIds(variantIds).stream()
                .filter(stock -> stock.status() == StockStatus.SELLABLE && stock.quantity() >= 1)
                .map(StockInfo::variantId)
                .collect(Collectors.toSet());
    }

    /** 둘 중 작은 금액을 고른다. */
    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}

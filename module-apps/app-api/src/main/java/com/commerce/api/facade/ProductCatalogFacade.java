package com.commerce.api.facade;

import com.commerce.api.facade.view.ProductSummaryView;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.info.ProductImageInfo;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductImageReader;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** 카탈로그 상품 목록 조회를 ACTIVE 변형·재고와 합성해 대표가·품절을 파생하는 파사드다. */
@Component
public class ProductCatalogFacade {

    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final StockReader stockReader;
    private final ProductImageReader imageReader;

    public ProductCatalogFacade(
            ProductReader productReader,
            ProductVariantReader variantReader,
            StockReader stockReader,
            ProductImageReader imageReader) {
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.stockReader = stockReader;
        this.imageReader = imageReader;
    }

    /** 노출 상품 페이지를 대표가·품절과 함께 반환한다. 키워드는 상품명 부분 일치로, 카테고리는 소속 일치로 좁히고, 정렬 기준을 따른다. */
    public Page<ProductSummaryView> getCatalogPage(
            @Nullable String keyword, @Nullable UUID categoryId, ProductSort sort, Pageable pageable) {
        // 1. 노출 상품 페이지 조회
        Page<ProductInfo> products =
                switch (sort) {
                    case LATEST -> productReader.getExposedPage(keyword, categoryId, pageable);
                    case PRICE_ASC -> productReader.getExposedPageOrderByPriceAsc(keyword, categoryId, pageable);
                    case PRICE_DESC -> productReader.getExposedPageOrderByPriceDesc(keyword, categoryId, pageable);
                };
        // 2. 합성 재료 수집 — ACTIVE 변형·재고·대표 이미지
        Map<UUID, List<ProductVariantInfo>> activeVariantsByProduct = activeVariantsByProduct(products.getContent());
        Set<UUID> orderableVariantIds = orderableVariantIds(activeVariantsByProduct);
        Map<UUID, String> primaryImageUrlByProduct = primaryImageUrlByProduct(products.getContent());
        // 3. 상품별 대표가·품절 파생
        return products.map(product -> summarize(
                product,
                activeVariantsByProduct.getOrDefault(product.id(), List.of()),
                orderableVariantIds,
                primaryImageUrlByProduct.get(product.id())));
    }

    /** 페이지에 실린 상품의 대표 이미지(정렬 순서 최솟값) URL을 상품별로 모은다. */
    private Map<UUID, String> primaryImageUrlByProduct(List<ProductInfo> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream().map(ProductInfo::id).toList();
        // 조회가 정렬 순서 오름차순이라 상품별 첫 항목이 대표다.
        return imageReader.getByProductIds(productIds).stream()
                .collect(Collectors.toMap(ProductImageInfo::productId, ProductImageInfo::url, (first, later) -> first));
    }

    /** 페이지에 실린 상품의 ACTIVE 변형을 상품별로 모은다. */
    private Map<UUID, List<ProductVariantInfo>> activeVariantsByProduct(List<ProductInfo> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream().map(ProductInfo::id).toList();
        return variantReader.getByProductIds(productIds).stream()
                .filter(variant -> variant.status() == ProductVariantStatus.ACTIVE)
                .collect(Collectors.groupingBy(ProductVariantInfo::productId));
    }

    /** 재고가 받쳐주는(SELLABLE ∧ 수량 1 이상) 변형 ID를 모은다. */
    private Set<UUID> orderableVariantIds(Map<UUID, List<ProductVariantInfo>> activeVariantsByProduct) {
        List<UUID> variantIds = activeVariantsByProduct.values().stream()
                .flatMap(List::stream)
                .map(ProductVariantInfo::id)
                .toList();
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        return stockReader.getByVariantIds(variantIds).stream()
                .filter(stock -> stock.status() == StockStatus.SELLABLE && stock.quantity() >= 1)
                .map(StockInfo::variantId)
                .collect(Collectors.toSet());
    }

    /** 상품 하나를 대표가·품절·대표 이미지 파생과 함께 목록 뷰로 옮긴다. */
    private static ProductSummaryView summarize(
            ProductInfo product,
            List<ProductVariantInfo> activeVariants,
            Set<UUID> orderableVariantIds,
            @Nullable String imageUrl) {
        Money fromPrice = null;
        boolean anyOrderable = false;
        for (ProductVariantInfo variant : activeVariants) {
            fromPrice = fromPrice == null ? variant.price() : min(fromPrice, variant.price());
            anyOrderable = anyOrderable || orderableVariantIds.contains(variant.id());
        }
        boolean soldOut = !activeVariants.isEmpty() && !anyOrderable;
        return new ProductSummaryView(product.id(), product.name(), fromPrice, soldOut, imageUrl);
    }

    /** 둘 중 작은 금액을 고른다. */
    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}

package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 상품 목록 조회를 합성한다. 노출 상품 페이지(판매중 ∧ 미삭제 ∧ ACTIVE 변형 1개 이상)에 변형·재고를
 * 배치(IN) 조회로 합성해 대표가·품절을 파생한다.
 *
 * <p>노출 필터는 페이지 산정(크기·총계)이 노출 집합과 일치하도록 상품 도메인 쿼리가 거르고, 이 파사드는 페이지에
 * 실린 상품의 대표가·품절 파생만 담당한다(상세와 같은 파생 규칙). 트랜잭션을 열지 않고 도메인 Reader를 조립한다.
 */
@Component
public class ProductCatalogFacade {

    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final StockReader stockReader;

    public ProductCatalogFacade(
            ProductReader productReader, ProductVariantReader variantReader, StockReader stockReader) {
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.stockReader = stockReader;
    }

    /** 노출 상품 페이지를 대표가·품절과 함께 최신 등록순으로 반환한다. */
    public Page<ProductSummaryView> getCatalogPage(Pageable pageable) {
        Page<ProductInfo> products = productReader.getExposedPage(pageable);
        Map<UUID, List<ProductVariantInfo>> activeVariantsByProduct = activeVariantsByProduct(products.getContent());
        Set<UUID> orderableVariantIds = orderableVariantIds(activeVariantsByProduct);
        return products.map(product ->
                summarize(product, activeVariantsByProduct.getOrDefault(product.id(), List.of()), orderableVariantIds));
    }

    private Map<UUID, List<ProductVariantInfo>> activeVariantsByProduct(List<ProductInfo> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream().map(ProductInfo::id).toList();
        return variantReader.getByProductIds(productIds).stream()
                .filter(variant -> variant.status() == ProductVariantStatus.ACTIVE)
                .collect(Collectors.groupingBy(ProductVariantInfo::productId));
    }

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

    private static ProductSummaryView summarize(
            ProductInfo product, List<ProductVariantInfo> activeVariants, Set<UUID> orderableVariantIds) {
        Money fromPrice = null;
        boolean anyOrderable = false;
        for (ProductVariantInfo variant : activeVariants) {
            fromPrice = fromPrice == null ? variant.price() : min(fromPrice, variant.price());
            anyOrderable = anyOrderable || orderableVariantIds.contains(variant.id());
        }
        boolean soldOut = !activeVariants.isEmpty() && !anyOrderable;
        return new ProductSummaryView(product.id(), product.name(), fromPrice, soldOut);
    }

    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}

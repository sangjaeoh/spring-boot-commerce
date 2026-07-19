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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 상품 상세 조회를 ACTIVE 변형·재고와 합성해 주문가능(orderable)·품절·대표가를 파생한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 Reader를 조립한다(각 Reader가 자기 트랜잭션 소유).
 */
@Component
public class ProductDetailFacade {

    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final StockReader stockReader;

    public ProductDetailFacade(
            ProductReader productReader, ProductVariantReader variantReader, StockReader stockReader) {
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.stockReader = stockReader;
    }

    /**
     * 상품 상세를 합성해 반환한다. 변형은 가격 오름차순으로 싣는다.
     *
     * @throws com.commerce.product.exception.ProductNotFoundException 노출 상품이 없으면(미존재·숨김·삭제 포함)
     */
    public ProductView getProductDetail(UUID productId) {
        ProductInfo product = productReader.getExposedProduct(productId);
        List<ProductVariantInfo> activeVariants = variantReader.getByProductId(productId).stream()
                .filter(variant -> variant.status() == ProductVariantStatus.ACTIVE)
                .toList();
        Set<UUID> orderableVariantIds = orderableVariantIds(activeVariants);
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
        return new ProductView(
                product.id(), product.name(), product.description(), product.status(), fromPrice, soldOut, variants);
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

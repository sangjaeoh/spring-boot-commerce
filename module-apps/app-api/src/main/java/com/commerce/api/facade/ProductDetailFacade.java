package com.commerce.api.facade;

import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.exception.StockNotFoundException;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 상품 상세 조회를 합성한다. 상품 그룹 + ACTIVE 변형 + 각 변형 재고로 주문가능·품절·대표가를 파생한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 Reader를 조립한다(각 Reader가 자기 readOnly 트랜잭션에서 Info로 변환).
 * 상품 노출 상태(ON_SALE/HIDDEN)는 파생하지 않고 status로 그대로 싣고, 변형별 주문가능(재고)과 품절 파생만
 * 담는다. 실제 주문 가능 여부(상품 ON_SALE ∧ 변형 ACTIVE ∧ 재고)는 체크아웃 게이트가 최종 판정한다. 변형은
 * 대표가 오름차순으로 정렬해 결정적 순서로 싣는다.
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
     * 상품 상세를 합성해 반환한다.
     *
     * @throws com.commerce.product.exception.ProductNotFoundException 활성 상품이 없으면
     */
    public ProductView getProductDetail(UUID productId) {
        ProductInfo product = productReader.getProduct(productId);
        List<ProductVariantView> variants = new ArrayList<>();
        Money fromPrice = null;
        boolean anyOrderable = false;
        for (ProductVariantInfo variant : variantReader.getByProductId(productId)) {
            if (variant.status() != ProductVariantStatus.ACTIVE) {
                continue;
            }
            boolean orderable = isOrderable(variant.id());
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

    private boolean isOrderable(UUID variantId) {
        StockInfo stock;
        try {
            stock = stockReader.getByVariantId(variantId);
        } catch (StockNotFoundException e) {
            return false;
        }
        return stock.status() == StockStatus.SELLABLE && stock.quantity() >= 1;
    }

    private static Money min(Money a, Money b) {
        return a.isLessThan(b) ? a : b;
    }
}

package com.commerce.api.facade;

import com.commerce.cart.info.CartInfo;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 장바구니 조회를 합성한다. 라인별 변형 현재가로 소계를 파생하고, 카탈로그와 같은 판매성 파생(변형 ACTIVE ∧
 * 상품 ON_SALE·미삭제 ∧ 재고 SELLABLE·수량 1 이상)으로 라인별 주문 가능(orderable)을 반영한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 Reader를 조립한다(변형·상품·재고 각 1회 IN 배치 조회). 단가는 저장하지 않고
 * 체크아웃과 같은 방식으로 변형 현재가를 조회 시점에 반영한다(장바구니는 "구매 예정" 목록). 변형은
 * 소프트삭제하지 않아 라인이 참조하는 변형은 항상 존재하고, 상품 부재(삭제)·재고 부재는 주문 불가로 강등한다.
 * 주문 불가 라인은 소계 표시는 유지하되 총액에서 제외한다 — 체크아웃이 전량 거부할 금액을 총액으로 보이지
 * 않게 하고, 라인 정리는 사용자가 직접 한다.
 */
@Component
public class CartViewFacade {

    private final CartReader cartReader;
    private final ProductVariantReader variantReader;
    private final ProductReader productReader;
    private final StockReader stockReader;

    public CartViewFacade(
            CartReader cartReader,
            ProductVariantReader variantReader,
            ProductReader productReader,
            StockReader stockReader) {
        this.cartReader = cartReader;
        this.variantReader = variantReader;
        this.productReader = productReader;
        this.stockReader = stockReader;
    }

    /** 회원의 장바구니를 변형 현재가·소계·주문 가능 파생·총액(주문 가능 라인 합)과 함께 반환한다. 없으면 빈 장바구니다. */
    public CartView getCartView(UUID memberId) {
        CartInfo cart = cartReader.getCart(memberId);
        List<UUID> variantIds =
                cart.items().stream().map(CartItemInfo::variantId).toList();
        Map<UUID, ProductVariantInfo> variants = variantReader.getVariants(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantInfo::id, Function.identity()));
        Set<UUID> exposedProductIds = exposedProductIds(variants.values());
        Set<UUID> inStockVariantIds = inStockVariantIds(variantIds);

        List<CartLineView> lines = new ArrayList<>();
        Money total = Money.ZERO;
        for (CartItemInfo item : cart.items()) {
            ProductVariantInfo variant =
                    Objects.requireNonNull(variants.get(item.variantId()), "장바구니 라인의 변형이 존재하지 않는다");
            Money subtotal = variant.price().multiply(item.quantity());
            boolean orderable = variant.status() == ProductVariantStatus.ACTIVE
                    && exposedProductIds.contains(variant.productId())
                    && inStockVariantIds.contains(variant.id());
            lines.add(new CartLineView(
                    item.variantId(), variant.optionLabel(), variant.price(), item.quantity(), subtotal, orderable));
            if (orderable) {
                total = total.plus(subtotal);
            }
        }
        lines.sort(Comparator.comparing(CartLineView::variantId));
        return new CartView(memberId, lines, total);
    }

    /** 라인 변형들이 속한 상품 중 노출(ON_SALE·미삭제) 상품 ID 집합 — 삭제된 상품은 조회에 없어 자연히 빠진다. */
    private Set<UUID> exposedProductIds(Collection<ProductVariantInfo> variants) {
        List<UUID> productIds =
                variants.stream().map(ProductVariantInfo::productId).distinct().toList();
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return productReader.getProducts(productIds).stream()
                .filter(product -> product.status() == ProductStatus.ON_SALE)
                .map(ProductInfo::id)
                .collect(Collectors.toSet());
    }

    /** 재고가 받쳐주는(SELLABLE ∧ 수량 1 이상) 변형 ID 집합 — 카탈로그 품절 파생과 같은 기준이다. */
    private Set<UUID> inStockVariantIds(List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        return stockReader.getByVariantIds(variantIds).stream()
                .filter(stock -> stock.status() == StockStatus.SELLABLE && stock.quantity() >= 1)
                .map(StockInfo::variantId)
                .collect(Collectors.toSet());
    }
}

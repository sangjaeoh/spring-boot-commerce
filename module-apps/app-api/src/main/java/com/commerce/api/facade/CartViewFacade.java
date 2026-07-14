package com.commerce.api.facade;

import com.commerce.cart.info.CartInfo;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductVariantReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 장바구니 조회를 합성한다. 라인별 변형 현재가를 조회해 소계·총액을 파생한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 Reader를 조립한다. 단가는 저장하지 않고 체크아웃과 같은 방식으로 변형 현재가를
 * 조회 시점에 반영한다(장바구니는 "구매 예정" 목록). 변형은 소프트삭제하지 않아 라인이 참조하는 변형은 항상 존재한다.
 */
@Component
public class CartViewFacade {

    private final CartReader cartReader;
    private final ProductVariantReader variantReader;

    public CartViewFacade(CartReader cartReader, ProductVariantReader variantReader) {
        this.cartReader = cartReader;
        this.variantReader = variantReader;
    }

    /** 회원의 장바구니를 변형 현재가·소계·총액과 함께 합성해 반환한다. 없으면 빈 장바구니다. */
    public CartView getCartView(UUID memberId) {
        CartInfo cart = cartReader.getCart(memberId);
        List<UUID> variantIds =
                cart.items().stream().map(CartItemInfo::variantId).toList();
        Map<UUID, ProductVariantInfo> variants = variantReader.getVariants(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantInfo::id, Function.identity()));

        List<CartLineView> lines = new ArrayList<>();
        Money total = Money.ZERO;
        for (CartItemInfo item : cart.items()) {
            ProductVariantInfo variant =
                    Objects.requireNonNull(variants.get(item.variantId()), "장바구니 라인의 변형이 존재하지 않는다");
            Money subtotal = variant.price().multiply(item.quantity());
            lines.add(new CartLineView(
                    item.variantId(), variant.optionLabel(), variant.price(), item.quantity(), subtotal));
            total = total.plus(subtotal);
        }
        lines.sort(Comparator.comparing(CartLineView::variantId));
        return new CartView(memberId, lines, total);
    }
}

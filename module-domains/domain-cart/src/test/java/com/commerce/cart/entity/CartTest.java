package com.commerce.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.cart.exception.CartItemNotFoundException;
import com.commerce.cart.exception.InvalidCartItemException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartTest {

    private int quantityOf(Cart cart, UUID variantId) {
        return cart.getItems().stream()
                .filter(item -> item.getVariantId().equals(variantId))
                .findFirst()
                .orElseThrow()
                .getQuantity();
    }

    @Test
    @DisplayName("생성 시 빈 장바구니다")
    void createsEmpty() {
        Cart cart = Cart.create(UUID.randomUUID());
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("같은 변형을 다시 담으면 수량을 합산한다")
    void sameVariantMergesQuantity() {
        Cart cart = Cart.create(UUID.randomUUID());
        UUID variantId = UUID.randomUUID();
        cart.addItem(variantId, 2);
        cart.addItem(variantId, 3);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(quantityOf(cart, variantId)).isEqualTo(5);
    }

    @Test
    @DisplayName("다른 변형은 별도 라인이 된다")
    void differentVariantsAreSeparateLines() {
        Cart cart = Cart.create(UUID.randomUUID());
        cart.addItem(UUID.randomUUID(), 1);
        cart.addItem(UUID.randomUUID(), 1);
        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("라인 수량을 바꾼다")
    void changeItemQuantity() {
        Cart cart = Cart.create(UUID.randomUUID());
        UUID variantId = UUID.randomUUID();
        cart.addItem(variantId, 2);
        cart.changeItemQuantity(variantId, 5);
        assertThat(quantityOf(cart, variantId)).isEqualTo(5);
    }

    @Test
    @DisplayName("없는 라인의 수량 변경·제거는 예외")
    void missingLineThrows() {
        Cart cart = Cart.create(UUID.randomUUID());
        UUID variantId = UUID.randomUUID();
        assertThatThrownBy(() -> cart.changeItemQuantity(variantId, 5)).isInstanceOf(CartItemNotFoundException.class);
        assertThatThrownBy(() -> cart.removeItem(variantId)).isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("라인을 제거한다")
    void removeItem() {
        Cart cart = Cart.create(UUID.randomUUID());
        UUID variantId = UUID.randomUUID();
        cart.addItem(variantId, 1);
        cart.removeItem(variantId);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("주어진 변형 라인들만 일괄 제거한다")
    void removeItemsRemovesGiven() {
        Cart cart = Cart.create(UUID.randomUUID());
        UUID keep = UUID.randomUUID();
        UUID drop1 = UUID.randomUUID();
        UUID drop2 = UUID.randomUUID();
        cart.addItem(keep, 1);
        cart.addItem(drop1, 1);
        cart.addItem(drop2, 1);
        cart.removeItems(Set.of(drop1, drop2));
        assertThat(cart.getItems()).hasSize(1);
        assertThat(quantityOf(cart, keep)).isEqualTo(1);
    }

    @Test
    @DisplayName("전체를 비운다")
    void clearEmpties() {
        Cart cart = Cart.create(UUID.randomUUID());
        cart.addItem(UUID.randomUUID(), 1);
        cart.addItem(UUID.randomUUID(), 1);
        cart.clear();
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("수량이 1 미만이면 담기·수량 변경 모두 거부한다")
    void rejectsQuantityBelowOne() {
        Cart cart = Cart.create(UUID.randomUUID());
        assertThatThrownBy(() -> cart.addItem(UUID.randomUUID(), 0)).isInstanceOf(InvalidCartItemException.class);
        UUID variantId = UUID.randomUUID();
        cart.addItem(variantId, 1);
        assertThatThrownBy(() -> cart.changeItemQuantity(variantId, 0)).isInstanceOf(InvalidCartItemException.class);
    }
}

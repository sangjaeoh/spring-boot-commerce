package com.commerce.cart.entity;

import com.commerce.cart.exception.CartErrorCode;
import com.commerce.cart.exception.CartItemNotFoundException;
import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 회원의 장바구니 애그리거트 루트다. 회원당 1개이며 자식 라인 집합을 소유한다.
 *
 * <p>같은 변형을 다시 담으면 새 라인 대신 기존 라인 수량을 합산한다.
 */
@Entity
@Table(schema = "cart", name = "cart")
public class Cart extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "member_id")
    private UUID memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CartItem> items = new HashSet<>();

    protected Cart() {}

    private Cart(UUID id, UUID memberId) {
        this.id = id;
        this.memberId = memberId;
    }

    /** 빈 장바구니를 생성한다. */
    public static Cart create(UUID memberId) {
        return new Cart(UuidV7Generator.generate(), memberId);
    }

    /** 변형을 담는다. 같은 변형이 있으면 수량을 합산한다. */
    public void addItem(UUID variantId, int quantity) {
        findItem(variantId)
                .ifPresentOrElse(
                        item -> item.addQuantity(quantity),
                        () -> items.add(CartItem.create(this, variantId, quantity)));
    }

    /**
     * 라인 수량을 바꾼다.
     *
     * @throws CartItemNotFoundException 해당 변형 라인이 없으면
     */
    public void changeItemQuantity(UUID variantId, int quantity) {
        requireItem(variantId).changeQuantity(quantity);
    }

    /**
     * 라인을 제거한다.
     *
     * @throws CartItemNotFoundException 해당 변형 라인이 없으면
     */
    public void removeItem(UUID variantId) {
        items.remove(requireItem(variantId));
    }

    /** 주어진 변형 라인들을 제거한다. 없는 라인은 무시한다. */
    public void removeItems(Set<UUID> variantIds) {
        items.removeIf(item -> variantIds.contains(item.getVariantId()));
    }

    /** 모든 라인을 제거한다. */
    public void clear() {
        items.clear();
    }

    private CartItem requireItem(UUID variantId) {
        return findItem(variantId).orElseThrow(() -> new CartItemNotFoundException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    private Optional<CartItem> findItem(UUID variantId) {
        return items.stream()
                .filter(item -> item.getVariantId().equals(variantId))
                .findFirst();
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public Set<CartItem> getItems() {
        return Collections.unmodifiableSet(items);
    }
}

package com.commerce.cart.domain;

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

/** 회원의 장바구니 애그리거트 루트다. 회원당 하나이며 담긴 라인 집합을 소유한다. */
@Entity
@Table(schema = "cart", name = "cart")
public class Cart extends BaseTimeEntity<UUID> {

    /** 장바구니 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 장바구니를 소유한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 장바구니 라인 집합. */
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

    /**
     * 변형을 담는다. 같은 변형이 있으면 수량을 합산한다.
     *
     * @throws InvalidCartItemException 수량이 1 미만이거나 합산 수량이 한도를 넘으면
     */
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
     * @throws InvalidCartItemException 수량이 1 미만이면
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

    /** 변형 라인을 찾고 없으면 거부한다. */
    private CartItem requireItem(UUID variantId) {
        return findItem(variantId).orElseThrow(() -> new CartItemNotFoundException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** 변형에 해당하는 라인을 찾는다. */
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

    /** 담긴 라인 집합을 변경 불가 뷰로 반환한다. */
    public Set<CartItem> getItems() {
        return Collections.unmodifiableSet(items);
    }
}

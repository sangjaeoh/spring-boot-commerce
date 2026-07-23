package com.commerce.domain.cart.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.cart.domain.exception.CartErrorCode;
import com.commerce.domain.cart.domain.exception.InvalidCartItemException;
import com.google.errorprone.annotations.Keep;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** 장바구니 라인이다. 담긴 변형(variant)과 수량을 보관한다. */
@Entity
@Table(schema = "cart", name = "cart_item")
public class CartItem extends BaseTimeEntity<UUID> {

    /** 장바구니 라인 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 부모 장바구니. */
    // Hibernate가 리플렉션으로 읽으나 자바 코드에선 읽지 않는다.
    @Keep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Cart cart;

    /** 담긴 변형 식별자. product 도메인 논리 참조. 한 장바구니 안에서 유니크하다. */
    @Column(name = "variant_id")
    private UUID variantId;

    /** 담은 수량. 1 이상. */
    @Column(name = "quantity")
    private int quantity;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    protected CartItem() {}

    private CartItem(UUID id, Cart cart, UUID variantId, int quantity) {
        this.id = id;
        this.cart = cart;
        this.variantId = variantId;
        this.quantity = quantity;
    }

    /**
     * 부모 장바구니에 속한 라인을 만든다.
     *
     * @throws InvalidCartItemException 수량이 1 미만이면
     */
    static CartItem create(Cart cart, UUID variantId, int quantity) {
        requirePositive(quantity);
        return new CartItem(UuidV7Generator.generate(), cart, variantId, quantity);
    }

    /**
     * 수량을 더한다.
     *
     * @throws InvalidCartItemException 더할 수량이 1 미만이거나 합산 수량이 한도를 넘으면
     */
    void addQuantity(int amount) {
        requirePositive(amount);
        // 합산 자체의 오버플로를 피하려 남은 여유(MAX - quantity)와 비교한다
        if (amount > Integer.MAX_VALUE - this.quantity) {
            throw new InvalidCartItemException(CartErrorCode.QUANTITY_LIMIT_EXCEEDED);
        }
        this.quantity += amount;
    }

    /**
     * 수량을 주어진 값으로 바꾼다.
     *
     * @throws InvalidCartItemException 수량이 1 미만이면
     */
    void changeQuantity(int newQuantity) {
        requirePositive(newQuantity);
        this.quantity = newQuantity;
    }

    /** 수량이 1 미만이면 거부한다. */
    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new InvalidCartItemException(CartErrorCode.INVALID_QUANTITY);
        }
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version;
    }
}

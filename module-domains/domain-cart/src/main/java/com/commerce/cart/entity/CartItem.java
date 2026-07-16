package com.commerce.cart.entity;

import com.commerce.cart.exception.CartErrorCode;
import com.commerce.cart.exception.InvalidCartItemException;
import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
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

/**
 * 장바구니 라인이다. 소속 장바구니는 애그리거트 내부 연관으로 참조한다.
 *
 * <p>생성·수정은 부모 {@link Cart}를 통해서만 한다. 동일 라인 동시 수량 합산(더블서밋)이 실재해
 * 낙관락({@code @Version})으로 합산 유실을 막는다 — 진 쪽은 충돌로 끝난다(409, 클라이언트 재시도).
 */
@Entity
@Table(schema = "cart", name = "cart_item")
public class CartItem extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    // Cart.items의 mappedBy 대상이자 FK(cart_id) 소유 필드다. Hibernate가 리플렉션으로 읽으나 자바 코드에선 읽지 않는다.
    @Keep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Cart cart;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "quantity")
    private int quantity;

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

    static CartItem create(Cart cart, UUID variantId, int quantity) {
        requirePositive(quantity);
        return new CartItem(UuidV7Generator.generate(), cart, variantId, quantity);
    }

    void addQuantity(int amount) {
        requirePositive(amount);
        this.quantity += amount;
    }

    void changeQuantity(int newQuantity) {
        requirePositive(newQuantity);
        this.quantity = newQuantity;
    }

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

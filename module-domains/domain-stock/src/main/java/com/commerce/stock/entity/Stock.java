package com.commerce.stock.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.exception.StockShortageException;
import com.commerce.stock.exception.StockStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * 변형별 재고 애그리거트 루트다. 변형당 1행이고, 동시 차감 경합이 실재해 낙관락({@code @Version})을 둔다.
 *
 * <p>소진(quantity=0)은 상태가 아니라 수량에서 파생 판정한다. 최초 상태는 {@code SELLABLE}이다.
 */
@Entity
@Table(schema = "stock", name = "stock")
public class Stock extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "quantity")
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StockStatus status;

    @Version
    @Column(name = "version")
    private long version;

    protected Stock() {}

    private Stock(UUID id, UUID variantId, int quantity) {
        this.id = id;
        this.variantId = variantId;
        this.quantity = quantity;
        this.status = StockStatus.SELLABLE;
    }

    /** 판매 가능({@code SELLABLE}) 상태로 초기 수량의 재고를 생성한다. */
    public static Stock create(UUID variantId, int initialQuantity) {
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("초기 수량은 0 이상이어야 한다: " + initialQuantity);
        }
        return new Stock(UuidV7Generator.generate(), variantId, initialQuantity);
    }

    /**
     * 재고를 차감한다.
     *
     * @throws StockShortageException 가용 수량이 차감 수량보다 적으면
     */
    public void deduct(int amount) {
        requirePositive(amount);
        if (quantity < amount) {
            throw new StockShortageException(StockErrorCode.STOCK_SHORTAGE);
        }
        this.quantity -= amount;
    }

    /** 상태와 무관하게 재고 수량을 되돌린다. */
    public void restore(int amount) {
        requirePositive(amount);
        this.quantity += amount;
    }

    /**
     * 재고를 재입고한다.
     *
     * @throws StockStatusException 단종 재고면
     */
    public void increase(int amount) {
        requirePositive(amount);
        if (status == StockStatus.DISCONTINUED) {
            throw new StockStatusException(StockErrorCode.CANNOT_INCREASE_DISCONTINUED);
        }
        this.quantity += amount;
    }

    /** 재고를 수동 품절로 둔다. */
    public void markSoldOut() {
        if (status != StockStatus.SELLABLE) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.SOLD_OUT;
    }

    /** 재고를 판매 가능으로 되돌린다. */
    public void markSellable() {
        if (status != StockStatus.SOLD_OUT) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.SELLABLE;
    }

    /** 재고를 단종한다. */
    public void discontinue() {
        if (status == StockStatus.DISCONTINUED) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.DISCONTINUED;
    }

    private static void requirePositive(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 한다: " + amount);
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

    public StockStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}

package com.commerce.stock.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** 변형(variant)별 재고 애그리거트 루트다. 변형당 한 행이며 가용 수량과 판매 가능 상태를 소유한다. */
@Entity
@Table(schema = "stock", name = "stock")
public class Stock extends BaseTimeEntity<UUID> {

    /** 재고 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 재고 대상 변형 식별자. product 도메인 논리 참조. 변형당 유니크하다. */
    @Column(name = "variant_id")
    private UUID variantId;

    /** 가용 수량. 0 이상. */
    @Column(name = "quantity")
    private int quantity;

    /** 판매 가능 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StockStatus status;

    /** 낙관락 버전. */
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

    /**
     * 재고를 수동 품절로 둔다.
     *
     * @throws StockStatusException 판매 가능 상태가 아니면
     */
    public void markSoldOut() {
        if (status != StockStatus.SELLABLE) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.SOLD_OUT;
    }

    /**
     * 재고를 판매 가능으로 되돌린다.
     *
     * @throws StockStatusException 품절 상태가 아니면
     */
    public void markSellable() {
        if (status != StockStatus.SOLD_OUT) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.SELLABLE;
    }

    /**
     * 재고를 단종한다.
     *
     * @throws StockStatusException 이미 단종된 재고면
     */
    public void discontinue() {
        if (status == StockStatus.DISCONTINUED) {
            throw new StockStatusException(StockErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = StockStatus.DISCONTINUED;
    }

    /** 수량이 1 미만이면 거부한다. */
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

package com.commerce.stock.info;

import com.commerce.stock.entity.Stock;
import com.commerce.stock.entity.StockStatus;
import java.time.Instant;
import java.util.UUID;

/** 변형별 재고 조회 경계 모델이다. */
public record StockInfo(
        UUID id, UUID variantId, int quantity, StockStatus status, Instant createdAt, Instant updatedAt) {

    public static StockInfo from(Stock stock) {
        return new StockInfo(
                stock.getId(),
                stock.getVariantId(),
                stock.getQuantity(),
                stock.getStatus(),
                stock.getCreatedAt(),
                stock.getUpdatedAt());
    }
}

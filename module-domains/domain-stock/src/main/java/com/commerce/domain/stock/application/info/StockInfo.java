package com.commerce.domain.stock.application.info;

import com.commerce.domain.stock.domain.Stock;
import com.commerce.domain.stock.domain.StockStatus;
import java.time.Instant;
import java.util.UUID;

/** 변형별 재고 조회 경계 모델이다. */
public record StockInfo(
        UUID id, UUID variantId, int quantity, StockStatus status, Instant createdAt, Instant updatedAt) {

    /** 재고 엔티티에서 조회 모델을 만든다. */
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

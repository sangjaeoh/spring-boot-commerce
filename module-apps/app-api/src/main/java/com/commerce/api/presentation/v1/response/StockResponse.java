package com.commerce.api.presentation.v1.response;

import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.info.StockInfo;
import java.time.Instant;
import java.util.UUID;

/** 변형별 재고 현황 응답이다. */
public record StockResponse(
        UUID id, UUID variantId, int quantity, StockStatus status, Instant createdAt, Instant updatedAt) {

    public static StockResponse from(StockInfo stock) {
        return new StockResponse(
                stock.id(), stock.variantId(), stock.quantity(), stock.status(), stock.createdAt(), stock.updatedAt());
    }
}

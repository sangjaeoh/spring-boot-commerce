package com.commerce.api.web.v1.admin.stock.response;

import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.info.StockInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** 변형별 재고 현황 응답이다. */
@Schema(description = "변형별 재고 현황 응답")
public record StockResponse(
        @Schema(description = "재고 ID") UUID id,
        @Schema(description = "변형 ID") UUID variantId,
        @Schema(description = "재고 수량") int quantity,
        @Schema(description = "재고 상태") StockStatus status,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

    public static StockResponse from(StockInfo stock) {
        return new StockResponse(
                stock.id(), stock.variantId(), stock.quantity(), stock.status(), stock.createdAt(), stock.updatedAt());
    }
}

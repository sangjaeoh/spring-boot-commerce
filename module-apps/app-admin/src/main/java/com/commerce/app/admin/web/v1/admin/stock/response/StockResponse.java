package com.commerce.app.admin.web.v1.admin.stock.response;

import com.commerce.domain.stock.application.info.StockInfo;
import com.commerce.domain.stock.domain.StockStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "변형별 재고 현황 응답")
public record StockResponse(
        @Schema(description = "재고 ID") UUID id,
        @Schema(description = "변형 ID") UUID variantId,
        @Schema(description = "재고 수량") int quantity,
        @Schema(description = "재고 상태") StockStatus status,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

    /** 재고 조회 모델에서 응답을 만든다. */
    public static StockResponse from(StockInfo stock) {
        return new StockResponse(
                stock.id(), stock.variantId(), stock.quantity(), stock.status(), stock.createdAt(), stock.updatedAt());
    }
}

package com.commerce.api.web.v1.admin.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "상품 등록 결과")
public record ProductRegistrationResponse(
        @Schema(description = "등록된 상품 ID") String productId) {

    public static ProductRegistrationResponse from(UUID productId) {
        return new ProductRegistrationResponse(productId.toString());
    }
}

package com.commerce.api.web.v1.admin.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "상품 이미지 업로드 결과")
public record ProductImageUploadResponse(
        @Schema(description = "업로드된 이미지 ID") String imageId) {

    /** 업로드된 이미지 ID에서 응답을 만든다. */
    public static ProductImageUploadResponse from(UUID imageId) {
        return new ProductImageUploadResponse(imageId.toString());
    }
}

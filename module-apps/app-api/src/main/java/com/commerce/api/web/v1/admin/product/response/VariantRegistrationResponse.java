package com.commerce.api.web.v1.admin.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "추가 변형 등록 결과")
public record VariantRegistrationResponse(
        @Schema(description = "등록된 변형 ID") String variantId) {

    /** 등록된 변형 ID에서 응답을 만든다. */
    public static VariantRegistrationResponse from(UUID variantId) {
        return new VariantRegistrationResponse(variantId.toString());
    }
}

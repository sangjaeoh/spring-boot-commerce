package com.commerce.api.web.v1.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 추가 변형 등록 결과다. 등록된 변형 ID를 문자열로 싣는다. */
@Schema(description = "추가 변형 등록 결과")
public record VariantRegistrationResponse(
        @Schema(description = "등록된 변형 ID") String variantId) {

    /** 변형 ID를 문자열로 담은 응답을 만든다. */
    public static VariantRegistrationResponse from(UUID variantId) {
        return new VariantRegistrationResponse(variantId.toString());
    }
}

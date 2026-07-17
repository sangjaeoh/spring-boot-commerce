package com.commerce.api.web.v1.admin.product.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 상품 등록 결과다. 등록된 상품 ID를 문자열로 싣는다. */
@Schema(description = "상품 등록 결과")
public record ProductRegistrationResponse(
        @Schema(description = "등록된 상품 ID") String productId) {

    /** 상품 ID를 문자열로 담은 응답을 만든다. */
    public static ProductRegistrationResponse from(UUID productId) {
        return new ProductRegistrationResponse(productId.toString());
    }
}

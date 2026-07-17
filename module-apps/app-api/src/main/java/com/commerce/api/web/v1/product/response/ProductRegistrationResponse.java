package com.commerce.api.web.v1.product.response;

import java.util.UUID;

/** 상품 등록 결과다. 등록된 상품 ID를 문자열로 싣는다. */
public record ProductRegistrationResponse(String productId) {

    /** 상품 ID를 문자열로 담은 응답을 만든다. */
    public static ProductRegistrationResponse from(UUID productId) {
        return new ProductRegistrationResponse(productId.toString());
    }
}

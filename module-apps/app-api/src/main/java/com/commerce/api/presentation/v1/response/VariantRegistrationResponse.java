package com.commerce.api.presentation.v1.response;

import java.util.UUID;

/** 추가 변형 등록 결과다. 등록된 변형 ID를 문자열로 싣는다. */
public record VariantRegistrationResponse(String variantId) {

    /** 변형 ID를 문자열로 담은 응답을 만든다. */
    public static VariantRegistrationResponse from(UUID variantId) {
        return new VariantRegistrationResponse(variantId.toString());
    }
}

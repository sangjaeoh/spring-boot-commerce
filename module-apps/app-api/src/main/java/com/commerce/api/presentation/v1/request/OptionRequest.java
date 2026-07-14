package com.commerce.api.presentation.v1.request;

import com.commerce.product.entity.ProductOption;
import jakarta.validation.constraints.NotBlank;

/** 상품 등록 시 첫 변형의 옵션 하나(옵션명·값)다. */
public record OptionRequest(@NotBlank String name, @NotBlank String value) {

    /** 도메인 옵션 입력으로 변환한다. */
    public ProductOption toProductOption() {
        return new ProductOption(name, value);
    }
}

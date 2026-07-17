package com.commerce.api.web.v1.product.request;

import com.commerce.product.entity.ProductOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 상품 등록 시 첫 변형의 옵션 하나(옵션명·값)다. */
public record OptionRequest(
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 40) String value) {

    /** 도메인 옵션 입력으로 변환한다. */
    public ProductOption toProductOption() {
        return new ProductOption(name, value);
    }
}

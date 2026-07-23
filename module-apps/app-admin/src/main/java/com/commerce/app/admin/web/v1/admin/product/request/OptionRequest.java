package com.commerce.app.admin.web.v1.admin.product.request;

import com.commerce.domain.product.domain.ProductOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "변형 옵션 하나(옵션명·값)")
public record OptionRequest(
        @Schema(description = "옵션명") @NotBlank @Size(max = 40)
        String name,

        @Schema(description = "옵션 값") @NotBlank @Size(max = 40)
        String value) {

    /** 도메인 옵션 입력으로 변환한다. */
    public ProductOption toProductOption() {
        return new ProductOption(name, value);
    }
}

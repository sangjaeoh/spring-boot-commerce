package com.commerce.api.web.v1.admin.product.request;

import com.commerce.product.entity.ProductOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "상품 등록 요청(첫 변형·초기 재고 시딩 포함)")
public record ProductRegistrationRequest(
        @Schema(description = "상품명") @NotBlank String name,

        @Schema(description = "상품 상세 설명", nullable = true) @Nullable
        String description,

        @Schema(description = "소속 카테고리 ID. 생략하면 미분류", nullable = true) @Nullable
        UUID categoryId,

        @Schema(description = "판매가(원 단위, 1 이상)") @NotNull @Positive
        Long price,

        @Schema(description = "변형 옵션 목록(비어 있으면 옵션 없는 단일 변형, 최대 10개)") @NotNull @Valid @Size(max = 10)
        List<@NotNull OptionRequest> options,

        @Schema(description = "초기 재고 수량(0 이상)") @NotNull @PositiveOrZero
        Integer initialQuantity) {

    /** 옵션 요청 목록을 도메인 옵션 입력 목록으로 변환한다. */
    public List<ProductOption> toProductOptions() {
        return options.stream().map(OptionRequest::toProductOption).toList();
    }
}

package com.commerce.api.web.v1.product.request;

import com.commerce.product.entity.ProductOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 기존 상품의 추가 변형 등록(재고 시딩 포함) 요청이다.
 *
 * <p>가격은 1원 이상, 초기 수량은 0 이상이다. 옵션 목록은 비어 있을 수 있고(옵션 없는 단일 변형),
 * 비어 있지 않으면 각 옵션의 이름·값이 필수다.
 */
public record VariantRegistrationRequest(
        @NotNull @Positive Long price,
        @NotNull @Valid @Size(max = 10) List<@NotNull OptionRequest> options,
        @NotNull @PositiveOrZero Integer initialQuantity) {

    /** 옵션 요청 목록을 도메인 옵션 입력 목록으로 변환한다. */
    public List<ProductOption> toProductOptions() {
        return options.stream().map(OptionRequest::toProductOption).toList();
    }
}

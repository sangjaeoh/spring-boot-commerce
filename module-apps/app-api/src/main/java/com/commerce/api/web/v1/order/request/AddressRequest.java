package com.commerce.api.web.v1.order.request;

import com.commerce.order.entity.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 체크아웃 배송지 요청이다. 상세 주소만 선택이고 나머지는 필수다. */
@Schema(description = "체크아웃 배송지 요청")
public record AddressRequest(
        @Schema(description = "수령인 이름") @NotBlank String recipientName,
        @Schema(description = "우편번호") @NotBlank String zipCode,
        @Schema(description = "도로명 주소") @NotBlank String roadAddress,

        @Schema(description = "상세 주소. 선택 항목", nullable = true) @Nullable
        String detailAddress,

        @Schema(description = "수령인 연락처") @NotBlank String phone) {

    /** 도메인 배송지 값 객체로 변환한다. */
    public Address toAddress() {
        return Address.of(recipientName, zipCode, roadAddress, detailAddress, phone);
    }
}

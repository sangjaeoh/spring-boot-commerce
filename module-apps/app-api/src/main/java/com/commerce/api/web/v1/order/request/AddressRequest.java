package com.commerce.api.web.v1.order.request;

import com.commerce.order.entity.Address;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 체크아웃 배송지 요청이다. 상세 주소만 선택이고 나머지는 필수다. */
public record AddressRequest(
        @NotBlank String recipientName,
        @NotBlank String zipCode,
        @NotBlank String roadAddress,
        @Nullable String detailAddress,
        @NotBlank String phone) {

    /** 도메인 배송지 값 객체로 변환한다. */
    public Address toAddress() {
        return Address.of(recipientName, zipCode, roadAddress, detailAddress, phone);
    }
}

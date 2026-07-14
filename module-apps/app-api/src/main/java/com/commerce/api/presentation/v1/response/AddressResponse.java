package com.commerce.api.presentation.v1.response;

import com.commerce.order.info.AddressInfo;
import org.jspecify.annotations.Nullable;

/** 배송지 응답이다. */
public record AddressResponse(
        String recipientName,
        String zipCode,
        String roadAddress,
        @Nullable String detailAddress,
        String phone) {

    public static AddressResponse from(AddressInfo address) {
        return new AddressResponse(
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone());
    }
}

package com.commerce.order.info;

import com.commerce.order.entity.Address;
import org.jspecify.annotations.Nullable;

/** 배송지 조회 경계 모델이다. */
public record AddressInfo(
        String recipientName,
        String zipCode,
        String roadAddress,
        @Nullable String detailAddress,
        String phone) {

    public static AddressInfo from(Address address) {
        return new AddressInfo(
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone());
    }
}

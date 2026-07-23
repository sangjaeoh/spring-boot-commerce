package com.commerce.domain.order.application.info;

import com.commerce.domain.order.domain.Address;
import org.jspecify.annotations.Nullable;

/** 배송지 조회 경계 모델이다. */
public record AddressInfo(
        String recipientName,
        String zipCode,
        String roadAddress,
        @Nullable String detailAddress,
        String phone) {

    /** 배송지 값 객체에서 조회 모델을 만든다. */
    public static AddressInfo from(Address address) {
        return new AddressInfo(
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone());
    }
}

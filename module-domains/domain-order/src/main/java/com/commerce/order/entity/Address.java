package com.commerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/** 배송지 값 객체다. */
@Embeddable
public record Address(
        @Column(name = "recipient_name") String recipientName,
        @Column(name = "zip_code") String zipCode,
        @Column(name = "road_address") String roadAddress,
        @Column(name = "detail_address") @Nullable String detailAddress,
        @Column(name = "phone") String phone) {

    public static Address of(
            String recipientName, String zipCode, String roadAddress, @Nullable String detailAddress, String phone) {
        return new Address(recipientName, zipCode, roadAddress, detailAddress, phone);
    }
}

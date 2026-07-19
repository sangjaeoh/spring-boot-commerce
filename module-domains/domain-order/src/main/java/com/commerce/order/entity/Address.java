package com.commerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/**
 * 배송지 값 객체다.
 *
 * @param recipientName 수령인 이름
 * @param zipCode 우편번호
 * @param roadAddress 도로명 주소
 * @param detailAddress 상세 주소. 없을 수 있다
 * @param phone 연락처
 */
@Embeddable
public record Address(
        @Column(name = "recipient_name") String recipientName,
        @Column(name = "zip_code") String zipCode,
        @Column(name = "road_address") String roadAddress,
        @Column(name = "detail_address") @Nullable String detailAddress,
        @Column(name = "phone") String phone) {

    /** 배송지를 만든다. */
    public static Address of(
            String recipientName, String zipCode, String roadAddress, @Nullable String detailAddress, String phone) {
        return new Address(recipientName, zipCode, roadAddress, detailAddress, phone);
    }
}

package com.commerce.domain.member.application.info;

import com.commerce.domain.member.domain.MemberAddress;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원 배송지 조회 경계 모델이다. */
public record MemberAddressInfo(
        UUID id,
        String recipientName,
        String zipCode,
        String roadAddress,
        @Nullable String detailAddress,
        String phone,
        boolean defaultAddress) {

    /** 배송지 엔티티에서 조회 모델을 만든다. */
    public static MemberAddressInfo from(MemberAddress address) {
        return new MemberAddressInfo(
                address.getId(),
                address.getRecipientName(),
                address.getZipCode(),
                address.getRoadAddress(),
                address.getDetailAddress(),
                address.getPhone(),
                address.isDefaultAddress());
    }
}

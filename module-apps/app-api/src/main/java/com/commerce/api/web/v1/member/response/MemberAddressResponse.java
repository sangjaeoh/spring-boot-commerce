package com.commerce.api.web.v1.member.response;

import com.commerce.member.info.MemberAddressInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(description = "회원 배송지 응답")
public record MemberAddressResponse(
        @Schema(description = "배송지 ID") UUID addressId,
        @Schema(description = "수령인 이름") String recipientName,
        @Schema(description = "우편번호") String zipCode,
        @Schema(description = "도로명 주소") String roadAddress,

        @Schema(description = "상세 주소", nullable = true) @Nullable
        String detailAddress,

        @Schema(description = "수령인 연락처") String phone,
        @Schema(description = "기본 배송지 여부") boolean defaultAddress) {

    /** 배송지 조회 모델에서 응답을 만든다. */
    public static MemberAddressResponse from(MemberAddressInfo address) {
        return new MemberAddressResponse(
                address.id(),
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone(),
                address.defaultAddress());
    }
}

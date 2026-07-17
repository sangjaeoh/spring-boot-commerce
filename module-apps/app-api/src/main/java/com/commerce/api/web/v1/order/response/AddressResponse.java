package com.commerce.api.web.v1.order.response;

import com.commerce.order.info.AddressInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** 배송지 응답이다. */
@Schema(description = "배송지 응답")
public record AddressResponse(
        @Schema(description = "수령인 이름") String recipientName,
        @Schema(description = "우편번호") String zipCode,
        @Schema(description = "도로명 주소") String roadAddress,

        @Schema(description = "상세 주소. 없으면 생략", nullable = true) @Nullable
        String detailAddress,

        @Schema(description = "수령인 연락처") String phone) {

    public static AddressResponse from(AddressInfo address) {
        return new AddressResponse(
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone());
    }
}

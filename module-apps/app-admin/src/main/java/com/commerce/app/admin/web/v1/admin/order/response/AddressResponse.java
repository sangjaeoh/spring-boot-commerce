package com.commerce.app.admin.web.v1.admin.order.response;

import com.commerce.domain.order.application.info.AddressInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

// app-api 본인/공개 슬라이스의 동명 응답과 같은 형상의 어드민 소유 사본이다(와이어 계약 동일).
@Schema(description = "배송지 응답")
public record AddressResponse(
        @Schema(description = "수령인 이름") String recipientName,
        @Schema(description = "우편번호") String zipCode,
        @Schema(description = "도로명 주소") String roadAddress,

        @Schema(description = "상세 주소. 없으면 생략", nullable = true) @Nullable
        String detailAddress,

        @Schema(description = "수령인 연락처") String phone) {

    /** 배송지 조회 모델에서 응답을 만든다. */
    public static AddressResponse from(AddressInfo address) {
        return new AddressResponse(
                address.recipientName(),
                address.zipCode(),
                address.roadAddress(),
                address.detailAddress(),
                address.phone());
    }
}

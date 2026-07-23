package com.commerce.app.api.web.v1.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "회원 배송지 등록 결과")
public record MemberAddressCreationResponse(
        @Schema(description = "등록된 배송지 ID(문자열)") String addressId) {

    /** 등록된 배송지 ID에서 응답을 만든다. */
    public static MemberAddressCreationResponse from(UUID addressId) {
        return new MemberAddressCreationResponse(addressId.toString());
    }
}

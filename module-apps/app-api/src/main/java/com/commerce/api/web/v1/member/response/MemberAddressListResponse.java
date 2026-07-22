package com.commerce.api.web.v1.member.response;

import com.commerce.member.application.info.MemberAddressInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "회원 배송지 목록 응답")
public record MemberAddressListResponse(
        @Schema(description = "배송지 목록(기본 우선·최신 등록순)") List<MemberAddressResponse> addresses) {

    public MemberAddressListResponse {
        addresses = List.copyOf(addresses);
    }

    /** 배송지 조회 모델 목록에서 응답을 만든다. */
    public static MemberAddressListResponse from(List<MemberAddressInfo> addresses) {
        return new MemberAddressListResponse(
                addresses.stream().map(MemberAddressResponse::from).toList());
    }
}

package com.commerce.api.web.v1.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 가입된 회원 ID를 문자열로 싣는다. */
@Schema(description = "회원 가입 결과")
public record MemberRegistrationResponse(
        @Schema(description = "가입된 회원 ID") String memberId) {

    public static MemberRegistrationResponse from(UUID memberId) {
        return new MemberRegistrationResponse(memberId.toString());
    }
}

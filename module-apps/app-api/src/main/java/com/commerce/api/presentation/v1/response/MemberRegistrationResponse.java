package com.commerce.api.presentation.v1.response;

import java.util.UUID;

/** 회원 가입 결과다. 가입된 회원 ID를 문자열로 싣는다. */
public record MemberRegistrationResponse(String memberId) {

    /** 회원 ID를 문자열로 담은 응답을 만든다. */
    public static MemberRegistrationResponse from(UUID memberId) {
        return new MemberRegistrationResponse(memberId.toString());
    }
}

package com.commerce.api.web.v1.member.response;

/** 로그인 결과다. Bearer 스킴으로 쓸 JWT 액세스 토큰을 싣는다. */
public record LoginResponse(String accessToken, String tokenType) {

    /** 액세스 토큰을 Bearer 타입으로 담은 응답을 만든다. */
    public static LoginResponse from(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}

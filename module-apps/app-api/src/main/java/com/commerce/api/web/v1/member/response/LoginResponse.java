package com.commerce.api.web.v1.member.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인 결과다. Bearer 스킴으로 쓸 JWT 액세스 토큰을 싣는다. */
@Schema(description = "로그인 결과")
public record LoginResponse(
        @Schema(description = "Bearer 스킴으로 쓸 JWT 액세스 토큰") String accessToken,
        @Schema(description = "토큰 타입(Bearer)") String tokenType) {

    /** 액세스 토큰을 Bearer 타입으로 담은 응답을 만든다. */
    public static LoginResponse from(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}

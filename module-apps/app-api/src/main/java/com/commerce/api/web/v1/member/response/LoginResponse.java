package com.commerce.api.web.v1.member.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 결과")
public record LoginResponse(
        @Schema(description = "Bearer 스킴으로 쓸 JWT 액세스 토큰") String accessToken,
        @Schema(description = "액세스 토큰 갱신에 쓰는 리프레시 토큰") String refreshToken,
        @Schema(description = "토큰 타입(Bearer)") String tokenType) {

    /** 발급된 액세스·리프레시 토큰을 조합해 응답을 만든다. */
    public static LoginResponse of(String accessToken, String refreshToken) {
        return new LoginResponse(accessToken, refreshToken, "Bearer");
    }
}

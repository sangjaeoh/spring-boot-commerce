package com.commerce.api.web.v1.member.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 결과")
public record TokenRefreshResponse(
        @Schema(description = "Bearer 스킴으로 쓸 JWT 액세스 토큰") String accessToken,
        @Schema(description = "토큰 타입(Bearer)") String tokenType) {

    /** 발급된 액세스 토큰에서 응답을 만든다. */
    public static TokenRefreshResponse from(String accessToken) {
        return new TokenRefreshResponse(accessToken, "Bearer");
    }
}

package com.commerce.auth.token;

import java.util.Map;

/** 검증을 통과한 토큰이 실은 클레임이다. 주체와 커스텀 클레임 맵을 담는다. */
public record TokenClaims(String subject, Map<String, String> claims) {
    public TokenClaims {
        claims = Map.copyOf(claims);
    }
}

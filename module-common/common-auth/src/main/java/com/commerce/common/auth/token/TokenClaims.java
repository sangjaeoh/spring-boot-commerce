package com.commerce.common.auth.token;

import java.util.Map;

/** 토큰이 실은 주체와 커스텀 클레임이다. */
public record TokenClaims(String subject, Map<String, String> claims) {
    public TokenClaims {
        claims = Map.copyOf(claims);
    }
}

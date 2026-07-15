package com.commerce.web.auth;

import java.util.UUID;

/**
 * 검증된 토큰에서 도출한 인증 주체다. {@link AuthTokenFilter}가 요청 속성 {@link #ATTRIBUTE}로 부착한다.
 */
public record AuthUser(UUID memberId) {

    /** 요청 속성 키. 값이 없으면 미인증 요청이다. */
    public static final String ATTRIBUTE = "com.commerce.web.auth.AuthUser";
}

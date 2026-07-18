package com.commerce.web.auth;

import java.util.UUID;

/**
 * 검증된 토큰에서 도출한 인증 주체다. {@link JwtAuthenticationFilter}가 시큐리티 컨텍스트의 principal로 싣는다.
 */
public record AuthUser(UUID memberId, String role) {}

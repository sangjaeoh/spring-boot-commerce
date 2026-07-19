package com.commerce.web.auth;

import java.util.UUID;

/** 검증된 토큰에서 도출한 인증 주체다. */
public record AuthUser(UUID memberId, String role) {}

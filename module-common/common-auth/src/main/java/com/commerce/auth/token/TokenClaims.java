package com.commerce.auth.token;

import java.util.UUID;

/** 검증을 통과한 토큰이 실은 클레임이다. 주체(회원 ID)와 역할을 담는다. */
public record TokenClaims(UUID subject, AuthRole role) {}

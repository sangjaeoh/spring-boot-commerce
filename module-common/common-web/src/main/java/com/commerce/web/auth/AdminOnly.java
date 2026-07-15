package com.commerce.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 토큰만 호출할 수 있는 핸들러 표시다. 컨트롤러 클래스에 붙이면 그 핸들러 전체에 적용된다.
 *
 * <p>{@link AdminOnlyInterceptor}가 강제한다 — 미인증 요청은 401, 관리자가 아닌 주체는 403으로 거부된다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {}

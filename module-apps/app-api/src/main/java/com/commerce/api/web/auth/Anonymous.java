package com.commerce.api.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 익명(미인증) 전용 표면을 표시하는 마커다. 공개 URL이면서 인증된 주체는 거부하는 컨트롤러 클래스 또는 핸들러
 * 메서드에 붙는다.
 *
 * <p>{@link PreAuthorize}("isAnonymous()")를 합성해 메서드 시큐리티가 인증된 주체를 403으로 거부한다 — 예: 로그인·가입.
 * 이 마커는 "인증된 주체 거부" 제약만 더할 뿐 공개(익명 도달) 자체를 열지는 않는다. 관객이 한 컨트롤러에 섞이면
 * 메서드 레벨로 관객을 표시한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAnonymous()")
public @interface Anonymous {}

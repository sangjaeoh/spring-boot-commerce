package com.commerce.api.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 로그인 사용자 전용 표면을 표시하는 마커다. 토큰 주체를 요구하는 컨트롤러 클래스 또는 핸들러 메서드에 붙는다.
 *
 * <p>{@link PreAuthorize}("isAuthenticated()")를 합성해 메서드 시큐리티가 미인증 요청을 거부한다(익명 401). 핸들러에
 * 붙어 URL 매핑이 바뀌어도 인증 요구가 남는다. 관객이 한 컨트롤러에 섞이면(예: 공개 가입과 본인 조회) 클래스로
 * 쪼개지 않고 메서드 레벨로 관객을 표시한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface Authenticated {}

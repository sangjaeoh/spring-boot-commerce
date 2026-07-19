package com.commerce.api.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 어드민 표면을 관리자 전용으로 잠그는 마커다. 어드민 컨트롤러 클래스에 붙는다.
 *
 * <p>{@link PreAuthorize}("hasRole('ADMIN')")를 합성해 메서드 시큐리티가 인가를 강제한다(미인증 익명 401·비관리자 403).
 * 어드민은 전용 {@code *AdminController}로 응집하므로 클래스 레벨로 선언한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface Admin {}

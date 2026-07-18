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
 * 어드민 URL 네임스페이스({@code /api/v{n}/admin/**})에 대한 시큐리티 필터 체인의 {@code hasRole('ADMIN')}과 함께 이중
 * 게이트를 이룬다 — 필터 체인이 1차, 이 마커가 2차다. 어드민은 전용 {@code *AdminController}로 응집하므로 클래스 레벨로
 * 선언하며 메서드 레벨은 아키텍처 테스트가 금지한다. 어드민 표면 정렬(아키텍처 테스트)과 OpenAPI bearer 요구
 * 도출({@code OpenApiConfig})의 기준이기도 하다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface Admin {}

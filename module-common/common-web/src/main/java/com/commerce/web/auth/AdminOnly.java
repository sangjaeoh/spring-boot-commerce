package com.commerce.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 어드민 표면 컨트롤러를 표시하는 마커다. 어드민 컨트롤러 클래스에 붙는다.
 *
 * <p>런타임 인증 강제는 이 마커가 아니라 어드민 URL 네임스페이스({@code /api/v{n}/admin/**})에 대한 시큐리티
 * 필터 체인({@code hasRole('ADMIN')})이 한다 — 미인증 익명은 401, 관리자가 아닌 주체는 403. 이 마커는 어드민
 * 표면 정렬(아키텍처 테스트)과 OpenAPI bearer 요구 도출({@code OpenApiConfig})의 기준이다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {}

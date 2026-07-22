package com.commerce.admin.web.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** 관리자 전용 표면을 표시하는 마커다. 클래스 레벨 전용이다 — 메서드 레벨 선언은 컴파일러가 차단한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface Admin {}

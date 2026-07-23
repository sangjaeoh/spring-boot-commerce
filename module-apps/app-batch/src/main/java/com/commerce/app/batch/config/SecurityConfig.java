package com.commerce.app.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 배치 앱의 HTTP 표면(웹훅·인프라 경로)만 열고 나머지를 거부하는 시큐리티 설정이다. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_INFRA_PATHS = {"/error", "/actuator/**"};
    // 발신자가 PG 시스템이라 토큰 인증 표면 밖이다 — HMAC 본문 서명은 컨트롤러가 검증한다.
    private static final String[] PUBLIC_PAYMENT_PATHS = {"/api/v1/payments/webhook"};

    /** 열거된 공개 경로만 열고 나머지는 전부 거부하는 무상태 체인을 공급한다 — 배치 앱엔 인증 주체가 없다. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_INFRA_PATHS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_PAYMENT_PATHS)
                        .permitAll()
                        .anyRequest()
                        .denyAll());
        return http.build();
    }
}

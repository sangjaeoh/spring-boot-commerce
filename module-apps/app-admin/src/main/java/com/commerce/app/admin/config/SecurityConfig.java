package com.commerce.app.admin.config;

import com.commerce.common.auth.token.JwtTokenCodec;
import com.commerce.common.web.auth.JwtAuthenticationFilter;
import com.commerce.common.web.auth.RestAccessDeniedHandler;
import com.commerce.common.web.auth.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/** 관리자 오퍼레이션 표면에 JWT 인증·ADMIN 역할을 강제하는 시큐리티 설정이다. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_INFRA_PATHS = {
        "/error", "/actuator/**", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] ADMIN_PATHS = {"/api/v1/admin/**"};

    /**
     * 인프라 공개 경로만 열고 어드민 URL 네임스페이스는 관리자 역할을, 나머지는 전부 거부하는 무상태 체인을
     * 공급한다 — 이 앱의 업무 표면은 어드민 URL뿐이다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenCodec jwtTokenCodec, ObjectMapper objectMapper)
            throws Exception {
        RestAuthenticationEntryPoint authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);
        http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_INFRA_PATHS)
                        .permitAll()
                        .requestMatchers(ADMIN_PATHS)
                        .hasRole("ADMIN")
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenCodec), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

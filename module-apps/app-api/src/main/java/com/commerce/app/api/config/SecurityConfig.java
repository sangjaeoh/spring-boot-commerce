package com.commerce.app.api.config;

import com.commerce.common.auth.token.JwtTokenCodec;
import com.commerce.common.web.auth.JwtAuthenticationFilter;
import com.commerce.common.web.auth.RestAccessDeniedHandler;
import com.commerce.common.web.auth.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/** JWT 인증 강제를 배선하는 시큐리티 설정이다. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_INFRA_PATHS = {
        "/error", "/actuator/**", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] PUBLIC_AUTH_PATHS = {
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/password-reset-request",
        "/api/v1/auth/password-reset"
    };
    private static final String[] PUBLIC_MEMBER_PATHS = {"/api/v1/members", "/api/v1/members/email-verification"};
    private static final String[] PUBLIC_PRODUCT_PATHS = {
        "/api/v1/products", "/api/v1/products/*", "/api/v1/products/*/reviews", "/api/v1/products/*/inquiries"
    };

    /** 열거된 공개 경로만 열고 나머지는 인증을 요구하는 무상태 체인을 공급한다. */
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
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_MEMBER_PATHS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_PRODUCT_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenCodec), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

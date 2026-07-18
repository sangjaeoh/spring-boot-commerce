package com.commerce.api.config;

import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.web.auth.JwtAuthenticationFilter;
import com.commerce.web.auth.RestAccessDeniedHandler;
import com.commerce.web.auth.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 무상태 JWT 인증 필터 체인을 조립한다.
 *
 * <p>세션·CSRF·폼로그인·HTTP Basic을 끄고({@link SessionCreationPolicy#STATELESS}) 토큰 검증 필터
 * ({@link JwtAuthenticationFilter})를 {@link UsernamePasswordAuthenticationFilter} 앞에 둔다. 공개 경로는
 * permitAll로 완전 열거하고, 어드민 URL 네임스페이스({@code /api/v1/admin/**})는 {@code hasRole('ADMIN')},
 * 나머지는 인증을 요구한다. 미인증 익명은 {@link RestAuthenticationEntryPoint}가 401, 권한 부족은
 * {@link RestAccessDeniedHandler}가 403으로 problem+json 응답한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_INFRA_PATHS = {
        "/error", "/actuator/**", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    private static final String[] PUBLIC_AUTH_PATHS = {"/api/v1/auth/login"};
    private static final String[] PUBLIC_MEMBER_PATHS = {"/api/v1/members"};
    private static final String[] PUBLIC_PRODUCT_PATHS = {"/api/v1/products", "/api/v1/products/*"};
    private static final String[] PUBLIC_PAYMENT_PATHS = {"/api/v1/payments/webhook"};
    private static final String[] ADMIN_PATHS = {"/api/v1/admin/**"};

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
                        .requestMatchers(HttpMethod.POST, PUBLIC_PAYMENT_PATHS)
                        .permitAll()
                        .requestMatchers(ADMIN_PATHS)
                        .hasRole("ADMIN")
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

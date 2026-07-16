package com.commerce.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 응답에 시큐리티 헤더 최소셋을 부착하는 필터다.
 *
 * <p>{@code X-Content-Type-Options: nosniff}로 MIME 스니핑을, {@code Cache-Control: no-store}로 응답
 * (토큰을 싣는 로그인 응답 포함) 캐싱을 막는다. 가장 바깥에서 돌아 필터 단계에서 거부된 응답(429·409 등)에도
 * 헤더가 실린다. JSON API 전제에서 보류한 헤더와 근거는 REQUIREMENTS.md 제약·전제가 소유한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(CONTENT_TYPE_OPTIONS, "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        filterChain.doFilter(request, response);
    }
}

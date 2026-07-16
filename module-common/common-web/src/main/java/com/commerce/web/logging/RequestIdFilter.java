package com.commerce.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 상관관계 ID를 배선하는 필터다.
 *
 * <p>클라이언트가 보낸 {@code X-Request-Id}를 수용하거나 없으면 UUID를 발급해, MDC({@code request_id})로
 * 요청 처리 중의 모든 구조화 로그 라인에 싣고 응답 헤더로 되돌린다 — 스윕·보상 경고처럼 요청 밖 로그가 아닌
 * 요청 스코프 로그의 상관관계를 제공한다. 형식을 벗어난 헤더(과길이·허용 밖 문자)는 로그 인젝션을 막기 위해
 * 신뢰하지 않고 새 ID로 대체한다. 요청 종료 시 MDC를 정리해 스레드 재사용 오염을 막는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "request_id";

    private static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String sanitize(@Nullable String header) {
        if (header == null
                || header.isBlank()
                || header.length() > MAX_LENGTH
                || !ALLOWED.matcher(header).matches()) {
            return UUID.randomUUID().toString();
        }
        return header;
    }
}

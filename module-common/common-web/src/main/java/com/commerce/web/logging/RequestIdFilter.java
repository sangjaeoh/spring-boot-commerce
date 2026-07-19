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
 * 요청 처리 중의 모든 구조화 로그 라인에 싣고 응답 헤더로 되돌린다. 스윕·보상 경고처럼 요청 밖에서 남는
 * 로그는 대상이 아니다.
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
            // 서블릿 스레드는 재사용되므로 지우지 않으면 다음 요청 로그에 이전 ID가 섞인다.
            MDC.remove(MDC_KEY);
        }
    }

    /** 헤더 값이 허용 형식이면 그대로 쓰고, 아니면 새 ID로 대체한다. */
    // 과길이·허용 밖 문자는 로그 인젝션 벡터라 클라이언트 값을 신뢰하지 않는다.
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

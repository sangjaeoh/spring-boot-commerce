package com.commerce.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 클라이언트 IP당 고정창 시도 한도를 강제하는 레이트리밋 필터다.
 *
 * <p>한 창 안에서 한도를 넘는 요청은 {@code 429} problem+json으로 거부하고 {@code Retry-After}에 창 길이를
 * 싣는다. 키는 소켓 원격 주소이며 프록시 헤더({@code X-Forwarded-For})는 신뢰하지 않는다. 저장소에 접근할 수
 * 없으면 시도를 통과시키지 않고 {@code 503}으로 거부한다(fail-closed). 어느 경로에 붙일지는 이 필터의 소비자가
 * 등록 시점에 정한다 — 이 필터 자신은 경로를 알지 못한다.
 */
public final class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimitStore store;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimitFilter(LoginRateLimitStore store, int maxAttempts, Duration window) {
        this.store = store;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long count;
        try {
            count = store.incrementAndCount(request.getRemoteAddr(), window);
        } catch (RuntimeException storeUnavailable) {
            writeProblem(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RATE_LIMIT_UNAVAILABLE",
                    "일시적으로 로그인을 처리할 수 없다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (count > maxAttempts) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(window.toSeconds()));
            writeProblem(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_LOGIN_ATTEMPTS",
                    "로그인 시도가 너무 많다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                        + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}

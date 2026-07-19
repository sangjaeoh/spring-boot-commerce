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
 * 싣는다. 키는 소비자가 준 {@link RateLimitScope}의 접두사에 소켓 원격 주소를 이은 값이며 프록시 헤더
 * ({@code X-Forwarded-For})는 신뢰하지 않는다. POST가 아닌 요청은 검사 없이 통과한다. 저장소에 접근할 수 없으면
 * 시도를 통과시키지 않고 {@code 503}으로 거부한다(fail-closed).
 */
public final class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String UNAVAILABLE_CODE = "RATE_LIMIT_UNAVAILABLE";

    private final LoginRateLimitStore store;
    private final RateLimitScope scope;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimitFilter(LoginRateLimitStore store, RateLimitScope scope, int maxAttempts, Duration window) {
        this.store = store;
        this.scope = scope;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    // 스로틀 근거인 "시도"(자격증명 추측·가입 열거)는 전부 POST다. 같은 경로의 다른 메서드는
    // 시도가 아니므로 버킷을 소모하지 않고 통과한다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long count;
        try {
            count = store.incrementAndCount(scope.keyPrefix() + request.getRemoteAddr(), window);
        } catch (RuntimeException storeUnavailable) {
            writeProblem(response, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_CODE, scope.unavailableDetail());
            return;
        }
        if (count > maxAttempts) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(window.toSeconds()));
            writeProblem(response, HttpStatus.TOO_MANY_REQUESTS, scope.tooManyCode(), scope.tooManyDetail());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 거부 응답을 problem+json 본문으로 쓴다. */
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

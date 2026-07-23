package com.commerce.common.web.idempotency;

import com.commerce.common.web.auth.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 더블서밋을 방어하는 멱등 필터다.
 *
 * <p>{@code Idempotency-Key} 헤더를 실은 unsafe 요청(POST·PUT·PATCH·DELETE)에 한해, 같은 키의 동시·
 * 즉시 재요청을 409로 거부한다. 키 축은 인증 주체(회원)+키다 — 서로 다른 회원의 같은 키는 충돌하지
 * 않는다. 키는 결과와 무관하게 창(TTL) 동안 잠기므로, 실패 요청의 재시도는 창 경과 후나 새 키로 한다.
 * 헤더가 없거나 safe 메서드면 멱등 검사를 건너뛴다. 저장소에 접근할 수 없으면 중복 차단 없이 요청을
 * 통과시키지 않고 {@code 503} problem+json으로 거부한다(fail-closed).
 */
@Component
public final class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || !UNSAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String scopedKey = scope(key);
        boolean acquired;
        try {
            acquired = store.tryBegin(scopedKey);
        } catch (RuntimeException storeUnavailable) {
            writeProblem(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IDEMPOTENCY_UNAVAILABLE",
                    "일시적으로 요청을 처리할 수 없다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (!acquired) {
            writeProblem(response, HttpStatus.CONFLICT, "DUPLICATE_REQUEST", "이미 처리 중이거나 처리된 요청이다.");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            store.complete(scopedKey);
        }
    }

    /** 저장소 키를 인증 주체 축으로 스코프한다. */
    private static String scope(String key) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.memberId() + ":" + key;
        }
        // 회원 축 키는 항상 UUID 접두라 anon 네임스페이스와 서로소다 — 미인증 요청이 회원 키를 선점하지 못한다.
        return "anon:" + key;
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

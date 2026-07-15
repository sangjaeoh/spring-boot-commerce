package com.commerce.web.auth;

import com.commerce.auth.token.JwtTokenCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 토큰이 유효하면 인증 주체({@link AuthUser})를 요청 속성으로 부착하는 필터다.
 *
 * <p>부착만 하고 강제하지 않는다 — 토큰이 없거나 유효하지 않아도 요청을 거부하지 않고 미인증으로 통과시킨다.
 * 엔드포인트별 인증 강제·소유권 검사는 이 필터의 소비자가 별도로 결정한다.
 */
@Component
public final class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenCodec jwtTokenCodec;

    public AuthTokenFilter(JwtTokenCodec jwtTokenCodec) {
        this.jwtTokenCodec = jwtTokenCodec;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtTokenCodec
                    .verify(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(memberId -> request.setAttribute(AuthUser.ATTRIBUTE, new AuthUser(memberId)));
        }
        filterChain.doFilter(request, response);
    }
}

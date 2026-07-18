package com.commerce.web.auth;

import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.auth.token.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 토큰이 유효하면 인증 주체({@link AuthUser})를 시큐리티 컨텍스트에 싣는 필터다.
 *
 * <p>fail-open이다 — 토큰이 없거나 유효하지 않으면 컨텍스트를 비운 채(익명) 통과시킨다. 요청 거부는 하지 않고
 * 인증·인가 강제는 시큐리티 필터 체인이 한다(미인증 익명은 진입점 401, 권한 부족은 접근거부 핸들러 403). 역할은
 * {@code ROLE_{역할}} 권한으로 매핑해 {@code hasRole('ADMIN')}이 {@code ROLE_ADMIN}을 요구하게 한다.
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenCodec jwtTokenCodec;

    public JwtAuthenticationFilter(JwtTokenCodec jwtTokenCodec) {
        this.jwtTokenCodec = jwtTokenCodec;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtTokenCodec.verify(header.substring(BEARER_PREFIX.length())).ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(TokenClaims claims) {
        AuthUser authUser = new AuthUser(claims.subject(), claims.role());
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + claims.role().name()));
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(authUser, null, authorities);
        // 공유 컨텍스트를 변이하지 않도록 새 컨텍스트를 만들어 세팅한다(무상태 — 저장소 지속 없음).
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}

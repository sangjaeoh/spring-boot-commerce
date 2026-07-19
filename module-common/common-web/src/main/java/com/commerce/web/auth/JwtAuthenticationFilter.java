package com.commerce.web.auth;

import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.auth.token.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
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
 * <p>fail-open이다 — 토큰이 없거나 유효하지 않으면 컨텍스트를 비운 채(익명) 통과시킨다. 역할 클레임은
 * {@code ROLE_{역할}} 권한으로 매핑한다.
 */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    // hasRole('ADMIN')이 ROLE_ADMIN 권한을 요구하므로 역할 앞에 이 접두사를 붙인다.
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ROLE_CLAIM = "role";

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

    /** 역할 클레임이 있으면 인증 주체를 만들어 시큐리티 컨텍스트에 싣는다. */
    private void authenticate(TokenClaims claims) {
        String role = claims.claims().get(ROLE_CLAIM);
        if (role == null) {
            return;
        }
        AuthUser authUser = new AuthUser(UUID.fromString(claims.subject()), role);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(authUser, null, authorities);
        // 공유 컨텍스트를 변이하지 않도록 새 컨텍스트를 만들어 세팅한다(무상태 — 저장소 지속 없음).
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}

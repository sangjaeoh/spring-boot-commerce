package com.commerce.common.web.auth;

import com.commerce.common.web.exception.WebErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * 미인증 요청(익명)에 401 problem+json을 쓰는 시큐리티 진입점이다.
 *
 * <p>응답 본문은 전역 핸들러의 401과 {@code $.code}·content-type·status 계약이 같다.
 */
public final class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // @RestControllerAdvice 바깥이라 ProblemDetail을 직접 직렬화하고 상태·content-type·charset을 수동 지정한다.
        WebErrorCode errorCode = WebErrorCode.UNAUTHENTICATED;
        ProblemDetail body = ProblemDetail.forStatus(errorCode.status());
        body.setDetail(errorCode.message());
        body.setProperty("code", errorCode.code());
        response.setStatus(errorCode.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

package com.commerce.common.web.auth;

import com.commerce.common.web.exception.WebErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증됐으나 권한이 부족한 요청에 403 problem+json을 쓰는 접근거부 핸들러다.
 *
 * <p>응답 본문은 전역 핸들러의 403과 {@code $.code}·content-type·status 계약이 같다.
 */
public final class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        // @RestControllerAdvice 바깥이라 ProblemDetail을 직접 직렬화하고 상태·content-type·charset을 수동 지정한다.
        WebErrorCode errorCode = WebErrorCode.FORBIDDEN;
        ProblemDetail body = ProblemDetail.forStatus(errorCode.status());
        body.setDetail(errorCode.message());
        body.setProperty("code", errorCode.code());
        response.setStatus(errorCode.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

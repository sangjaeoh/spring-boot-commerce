package com.commerce.web.error;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 경계에 도달한 예외를 RFC 9457 {@link ProblemDetail}로 변환하는 전역 핸들러다.
 *
 * <p>{@link BaseException}은 실린 {@link ErrorCode}의 상태·코드·메시지로, Bean Validation 실패는
 * 400과 필드 오류로, 낙관락 충돌은 409로, 그 밖의 예외는 500으로 매핑한다. 표준 스프링 MVC 예외
 * (405·415·본문 파싱 오류 등)는 {@link ResponseEntityExceptionHandler}가 각자의 상태로 처리하므로
 * 500 폴백이 이를 삼키지 않는다.
 */
@RestControllerAdvice
public class ProblemDetailHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";
    private static final String INVALID_VALUE = "유효하지 않은 값이다.";

    /** {@link BaseException}을 {@link ErrorCode#status()} 상태의 ProblemDetail로 매핑한다. */
    @ExceptionHandler(BaseException.class)
    protected ResponseEntity<Object> handleBaseException(BaseException exception, WebRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        HttpStatusCode status = HttpStatusCode.valueOf(errorCode.status());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, errorCode.message());
        body.setProperty(CODE, errorCode.code());
        return respond(exception, body, status, request);
    }

    /**
     * 낙관락 충돌을 409로 매핑한다. 서버는 재시도하지 않고 클라이언트가 재시도한다
     * (entity-persistence.md).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    protected ResponseEntity<Object> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "동시 요청으로 충돌이 발생했다. 다시 시도해 주세요.");
        body.setProperty(CODE, "CONCURRENT_MODIFICATION");
        return respond(exception, body, HttpStatus.CONFLICT, request);
    }

    /**
     * 매핑되지 않은 예외를 500으로 매핑한다. 호출자 보장 선행조건 위반이 경계로 새어 나온 서버 버그
     * ({@link IllegalArgumentException} 등)가 여기에 온다.
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        logger.error("처리되지 않은 예외가 경계에 도달했다", exception);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했다.");
        body.setProperty(CODE, "INTERNAL_ERROR");
        return respond(exception, body, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "요청 값이 유효하지 않다.");
        body.setProperty(CODE, "VALIDATION_FAILED");
        BindingResult result = exception.getBindingResult();
        List<Map<String, String>> errors = Stream.concat(
                        result.getFieldErrors().stream()
                                .map(error -> violation(error.getField(), error.getDefaultMessage())),
                        result.getGlobalErrors().stream()
                                .map(error -> violation(error.getObjectName(), error.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return Objects.requireNonNull(handleExceptionInternal(exception, body, headers, status, request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "요청 값이 유효하지 않다.");
        body.setProperty(CODE, "VALIDATION_FAILED");
        List<Map<String, String>> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> {
                    // -parameters 미컴파일 클래스패스에서는 파라미터명이 없을 수 있다.
                    String parameter = Objects.requireNonNullElse(
                            result.getMethodParameter().getParameterName(), "parameter");
                    return result.getResolvableErrors().stream()
                            .map(error -> violation(parameter, error.getDefaultMessage()));
                })
                .toList();
        body.setProperty("errors", errors);
        return Objects.requireNonNull(handleExceptionInternal(exception, body, headers, status, request));
    }

    private static Map<String, String> violation(String field, @Nullable String message) {
        return Map.of("field", field, "message", Objects.requireNonNullElse(message, INVALID_VALUE));
    }

    private ResponseEntity<Object> respond(
            Exception exception, ProblemDetail body, HttpStatusCode status, WebRequest request) {
        // 응답 미커밋 컨텍스트라 handleExceptionInternal은 non-null이다(커밋 시에만 null 반환).
        return Objects.requireNonNull(handleExceptionInternal(exception, body, new HttpHeaders(), status, request));
    }
}

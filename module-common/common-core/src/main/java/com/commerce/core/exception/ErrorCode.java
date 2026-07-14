package com.commerce.core.exception;

/**
 * 경계 응답(ProblemDetail)이 소비하는 에러 코드 계약이다.
 *
 * <p>도메인마다 {@code {Name}ErrorCode} enum이 이를 구현해 코드 문자열·사용자 메시지·HTTP 상태를
 * 채운다. 프레임워크에 의존하지 않도록 상태는 원시 정수(HTTP status code)로 노출한다.
 */
public interface ErrorCode {

    /** 기계 판독용 에러 코드 문자열을 반환한다. */
    String code();

    /** 사용자에게 노출할 메시지를 반환한다. */
    String message();

    /** 매핑되는 HTTP 상태 코드를 반환한다. */
    int status();
}

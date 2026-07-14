package com.commerce.core.exception;

/**
 * 경계에 도달하는 도메인 예외의 최상위 타입이다.
 *
 * <p>{@link ErrorCode}를 실어 ProblemDetail 핸들러가 코드·메시지·상태로 응답을 만들게 한다.
 * unchecked라 {@code @Transactional} 기본 롤백이 걸린다. 국소에서 잡아 처리하는 예외는
 * 이를 상속하지 않고 JDK 관용구를 쓴다.
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

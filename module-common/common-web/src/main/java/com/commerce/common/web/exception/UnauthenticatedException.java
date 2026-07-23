package com.commerce.common.web.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 인증 주체가 필요한 요청에 유효한 토큰이 없을 때 던지는 예외다. */
public class UnauthenticatedException extends BaseException {

    public UnauthenticatedException(ErrorCode errorCode) {
        super(errorCode);
    }
}

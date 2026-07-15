package com.commerce.web.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 인증 주체가 필요한 요청에 유효한 토큰이 없을 때 던진다. */
public class UnauthenticatedException extends BaseException {

    public UnauthenticatedException(ErrorCode errorCode) {
        super(errorCode);
    }
}

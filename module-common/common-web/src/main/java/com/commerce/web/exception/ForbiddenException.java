package com.commerce.web.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 인증 주체에 요청한 오퍼레이션의 권한이 없을 때 던진다. */
public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
}

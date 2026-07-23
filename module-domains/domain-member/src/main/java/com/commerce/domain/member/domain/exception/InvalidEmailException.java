package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 이메일 형식이 올바르지 않을 때 던지는 예외다. */
public class InvalidEmailException extends BaseException {

    public InvalidEmailException(ErrorCode errorCode) {
        super(errorCode);
    }
}

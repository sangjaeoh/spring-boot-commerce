package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때 던지는 예외다. */
public class InvalidPasswordException extends BaseException {

    public InvalidPasswordException(ErrorCode errorCode) {
        super(errorCode);
    }
}

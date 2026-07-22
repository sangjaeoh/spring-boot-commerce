package com.commerce.member.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 현재 패스워드가 저장된 자격증명과 일치하지 않을 때 던지는 예외다. */
public class PasswordMismatchException extends BaseException {

    public PasswordMismatchException(ErrorCode errorCode) {
        super(errorCode);
    }
}

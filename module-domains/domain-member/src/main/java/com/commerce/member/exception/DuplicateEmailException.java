package com.commerce.member.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 활성 회원 사이에서 이미 쓰이는 이메일로 가입을 시도할 때 던지는 예외다. */
public class DuplicateEmailException extends BaseException {

    public DuplicateEmailException(ErrorCode errorCode) {
        super(errorCode);
    }
}

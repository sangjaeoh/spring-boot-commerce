package com.commerce.member.domain.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 활성 회원을 찾지 못했을 때 던지는 예외다. */
public class MemberNotFoundException extends BaseException {

    public MemberNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

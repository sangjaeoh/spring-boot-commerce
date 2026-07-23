package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 활성 회원을 찾지 못했을 때 던지는 예외다. */
public class MemberNotFoundException extends BaseException {

    public MemberNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

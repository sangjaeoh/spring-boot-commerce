package com.commerce.member.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 회원 상태 전이를 시도할 때 던진다. */
public class MemberStatusException extends BaseException {

    public MemberStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}

package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 허용되지 않은 회원 상태 전이를 시도할 때 던지는 예외다. */
public class MemberStatusException extends BaseException {

    public MemberStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}

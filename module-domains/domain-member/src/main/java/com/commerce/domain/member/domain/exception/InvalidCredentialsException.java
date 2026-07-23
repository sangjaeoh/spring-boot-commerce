package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 자격증명 검증이 실패할 때 던지는 예외다. 미존재·탈퇴·패스워드 불일치를 구분하지 않는다. */
public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException(ErrorCode errorCode) {
        super(errorCode);
    }
}

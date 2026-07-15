package com.commerce.member.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 자격증명 검증이 실패할 때 던진다. 미존재·탈퇴·패스워드 불일치를 구분하지 않는다(계정 존재 노출 방지). */
public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException(ErrorCode errorCode) {
        super(errorCode);
    }
}

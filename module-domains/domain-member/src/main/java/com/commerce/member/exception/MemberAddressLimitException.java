package com.commerce.member.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 회원당 배송지 개수 한도를 넘겨 등록할 때 던지는 예외다. */
public class MemberAddressLimitException extends BaseException {

    public MemberAddressLimitException(ErrorCode errorCode) {
        super(errorCode);
    }
}

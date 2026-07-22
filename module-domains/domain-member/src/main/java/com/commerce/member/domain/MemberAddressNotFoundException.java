package com.commerce.member.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 배송지가 없거나 타인 소유일 때 던지는 예외다. */
public class MemberAddressNotFoundException extends BaseException {

    public MemberAddressNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

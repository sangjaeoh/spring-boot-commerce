package com.commerce.domain.member.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 배송지가 없거나 타인 소유일 때 던지는 예외다. */
public class MemberAddressNotFoundException extends BaseException {

    public MemberAddressNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

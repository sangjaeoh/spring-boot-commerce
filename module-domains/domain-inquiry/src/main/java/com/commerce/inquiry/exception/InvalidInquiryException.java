package com.commerce.inquiry.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 문의·답변 본문이 허용 범위를 벗어났을 때 던지는 예외다. */
public class InvalidInquiryException extends BaseException {

    public InvalidInquiryException(ErrorCode errorCode) {
        super(errorCode);
    }
}

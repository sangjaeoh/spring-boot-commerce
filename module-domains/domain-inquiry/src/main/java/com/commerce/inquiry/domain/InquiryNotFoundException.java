package com.commerce.inquiry.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 문의가 없을 때 던지는 예외다. */
public class InquiryNotFoundException extends BaseException {

    public InquiryNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

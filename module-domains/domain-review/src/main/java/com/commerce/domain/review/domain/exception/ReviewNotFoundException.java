package com.commerce.domain.review.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 대상 활성 리뷰가 없을 때 던지는 예외다. */
public class ReviewNotFoundException extends BaseException {

    public ReviewNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

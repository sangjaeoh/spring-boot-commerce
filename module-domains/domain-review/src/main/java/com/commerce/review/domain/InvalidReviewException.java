package com.commerce.review.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 별점·본문이 허용 범위를 벗어났을 때 던지는 예외다. */
public class InvalidReviewException extends BaseException {

    public InvalidReviewException(ErrorCode errorCode) {
        super(errorCode);
    }
}

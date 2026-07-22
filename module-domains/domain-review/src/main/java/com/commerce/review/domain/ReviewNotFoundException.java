package com.commerce.review.domain;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 본인 소유 리뷰가 없을 때 던지는 예외다. */
public class ReviewNotFoundException extends BaseException {

    public ReviewNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

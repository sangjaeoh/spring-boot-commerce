package com.commerce.domain.review.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 같은 상품에 이미 리뷰가 있을 때 던지는 예외다. */
public class DuplicateReviewException extends BaseException {

    public DuplicateReviewException(ErrorCode errorCode) {
        super(errorCode);
    }
}

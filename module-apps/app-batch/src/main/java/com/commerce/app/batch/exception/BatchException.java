package com.commerce.app.batch.exception;

import com.commerce.common.core.exception.BaseException;

/** 웹훅 수신이 통지를 거부할 때 던지는 예외다. */
public class BatchException extends BaseException {

    public BatchException(BatchErrorCode errorCode) {
        super(errorCode);
    }
}

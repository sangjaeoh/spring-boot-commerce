package com.commerce.stock.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 이미 재고가 있는 변형에 재고를 생성하려 할 때 던지는 예외다. */
public class DuplicateStockException extends BaseException {

    public DuplicateStockException(ErrorCode errorCode) {
        super(errorCode);
    }
}

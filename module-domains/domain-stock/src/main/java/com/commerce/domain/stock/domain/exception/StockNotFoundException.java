package com.commerce.domain.stock.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 변형의 재고를 찾지 못했을 때 던지는 예외다. */
public class StockNotFoundException extends BaseException {

    public StockNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

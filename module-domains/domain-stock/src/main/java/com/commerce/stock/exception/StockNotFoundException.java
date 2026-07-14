package com.commerce.stock.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 변형의 재고를 찾지 못했을 때 던진다. */
public class StockNotFoundException extends BaseException {

    public StockNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

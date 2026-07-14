package com.commerce.stock.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 차감 수량이 가용 재고를 초과할 때 던진다. */
public class StockShortageException extends BaseException {

    public StockShortageException(ErrorCode errorCode) {
        super(errorCode);
    }
}

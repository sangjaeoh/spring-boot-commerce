package com.commerce.domain.stock.domain.exception;

import com.commerce.common.core.exception.BaseException;
import com.commerce.common.core.exception.ErrorCode;

/** 차감 수량이 가용 재고를 초과할 때 던지는 예외다. */
public class StockShortageException extends BaseException {

    public StockShortageException(ErrorCode errorCode) {
        super(errorCode);
    }
}

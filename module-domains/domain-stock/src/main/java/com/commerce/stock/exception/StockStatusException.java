package com.commerce.stock.exception;

import com.commerce.core.exception.BaseException;
import com.commerce.core.exception.ErrorCode;

/** 허용되지 않은 재고 상태 전이·상태 기반 거부(단종 재입고 등)를 시도할 때 던진다. */
public class StockStatusException extends BaseException {

    public StockStatusException(ErrorCode errorCode) {
        super(errorCode);
    }
}

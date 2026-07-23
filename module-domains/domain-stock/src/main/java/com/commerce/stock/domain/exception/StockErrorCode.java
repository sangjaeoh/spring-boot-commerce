package com.commerce.stock.domain.exception;

import com.commerce.core.exception.ErrorCode;

public enum StockErrorCode implements ErrorCode {
    STOCK_NOT_FOUND("STOCK_NOT_FOUND", "재고를 찾을 수 없다.", 404),
    DUPLICATE_STOCK("STOCK_DUPLICATE", "이미 재고가 존재하는 변형이다.", 409),
    INVALID_STATE_TRANSITION("STOCK_INVALID_STATE_TRANSITION", "허용되지 않은 재고 상태 전이다.", 409),
    STOCK_SHORTAGE("STOCK_SHORTAGE", "재고 수량이 부족하다.", 409),
    CANNOT_INCREASE_DISCONTINUED("STOCK_CANNOT_INCREASE_DISCONTINUED", "단종 재고는 재입고할 수 없다.", 409);

    private final String code;
    private final String message;
    private final int status;

    StockErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}

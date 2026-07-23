package com.commerce.review.domain.exception;

import com.commerce.core.exception.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {
    REVIEW_NOT_FOUND("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없다.", 404),
    ALREADY_WRITTEN("REVIEW_ALREADY_WRITTEN", "이미 리뷰를 쓴 상품이다.", 409),
    INVALID_RATING("REVIEW_INVALID_RATING", "별점은 1~5여야 한다.", 400),
    INVALID_CONTENT("REVIEW_INVALID_CONTENT", "본문은 1~1000자여야 한다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ReviewErrorCode(String code, String message, int status) {
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

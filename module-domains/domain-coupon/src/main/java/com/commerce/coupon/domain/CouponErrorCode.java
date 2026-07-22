package com.commerce.coupon.domain;

import com.commerce.core.exception.ErrorCode;

public enum CouponErrorCode implements ErrorCode {
    COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없다.", 404),
    ISSUED_COUPON_NOT_FOUND("ISSUED_COUPON_NOT_FOUND", "발급 쿠폰을 찾을 수 없다.", 404),
    INVALID_COUPON_STATE_TRANSITION("COUPON_INVALID_STATE_TRANSITION", "허용되지 않은 쿠폰 상태 전이다.", 409),
    COUPON_DISABLED("COUPON_DISABLED", "발급 중지된 쿠폰이다.", 409),
    COUPON_OUTSIDE_ISSUE_PERIOD("COUPON_OUTSIDE_ISSUE_PERIOD", "발급 가능 기간이 아니다.", 409),
    DUPLICATE_ISSUANCE("COUPON_DUPLICATE_ISSUANCE", "회원당 동일 쿠폰은 한 번만 발급된다.", 409),
    ISSUANCE_LIMIT_EXHAUSTED("COUPON_ISSUANCE_LIMIT_EXHAUSTED", "쿠폰 발급 한도가 소진됐다.", 409),
    ISSUED_COUPON_NOT_USABLE("ISSUED_COUPON_NOT_USABLE", "사용할 수 없는 발급 쿠폰이다.", 409),
    ISSUED_COUPON_NOT_REVOCABLE("ISSUED_COUPON_NOT_REVOCABLE", "무효화할 수 없는 발급 쿠폰이다.", 409),
    COUPON_EXPIRED("COUPON_EXPIRED", "발급 쿠폰의 사용 기한이 지났다.", 409),
    INVALID_DISCOUNT("COUPON_INVALID_DISCOUNT", "할인 정책이 올바르지 않다.", 400),
    INVALID_VALIDITY_PERIOD("COUPON_INVALID_VALIDITY_PERIOD", "유효 기간이 올바르지 않다.", 400),
    INVALID_USAGE_VALID_DAYS("COUPON_INVALID_USAGE_VALID_DAYS", "사용 창(일)은 1 이상이어야 한다.", 400),
    INVALID_MAX_ISSUANCE("COUPON_INVALID_MAX_ISSUANCE", "발급 한도는 1 이상이어야 한다.", 400);

    private final String code;
    private final String message;
    private final int status;

    CouponErrorCode(String code, String message, int status) {
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

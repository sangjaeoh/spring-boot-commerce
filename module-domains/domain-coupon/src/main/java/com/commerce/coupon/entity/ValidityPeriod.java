package com.commerce.coupon.entity;

import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.InvalidCouponException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * 쿠폰의 발급 가능 기간 값 객체다.
 *
 * <p>발급 창이지 발급분 사용 만료가 아니다(사용 만료는 발급분 expiresAt).
 */
@Embeddable
public record ValidityPeriod(
        @Column(name = "valid_from") Instant validFrom,
        @Column(name = "valid_until") Instant validUntil) {

    public ValidityPeriod {
        if (!validFrom.isBefore(validUntil)) {
            throw new InvalidCouponException(CouponErrorCode.INVALID_VALIDITY_PERIOD);
        }
    }

    /**
     * 발급 가능 기간을 만든다.
     *
     * @throws InvalidCouponException 시작 시각이 종료 시각보다 앞서지 않으면
     */
    public static ValidityPeriod of(Instant validFrom, Instant validUntil) {
        return new ValidityPeriod(validFrom, validUntil);
    }

    public boolean isValidAt(Instant now) {
        return !now.isBefore(validFrom) && !now.isAfter(validUntil);
    }
}

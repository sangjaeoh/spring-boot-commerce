package com.commerce.coupon.domain;

import com.commerce.coupon.domain.exception.CouponErrorCode;
import com.commerce.coupon.domain.exception.InvalidCouponException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * 쿠폰의 발급 가능 기간 값 객체다.
 *
 * @param validFrom 발급 가능 시작 시각
 * @param validUntil 발급 가능 종료 시각. 시작 시각보다 뒤다
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

    /** 주어진 시각이 발급 가능 기간 안(양 끝 포함)인지 본다. */
    public boolean isValidAt(Instant now) {
        return !now.isBefore(validFrom) && !now.isAfter(validUntil);
    }
}

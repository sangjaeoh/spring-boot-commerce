package com.commerce.coupon.repository;

import com.commerce.coupon.entity.Coupon;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /**
     * 발급 한도 내에서 발급 카운트를 원자적으로 선점하고 갱신 행 수를 반환한다. 0이면 한도 소진이다.
     *
     * <p>조건부 UPDATE가 행 락으로 동시 발급을 직렬화해 한도 초과를 배제한다. 무제한 정책
     * ({@code maxIssuance == null})은 비교가 성립하지 않아 항상 0을 반환하므로 호출하지 않는다.
     */
    @Modifying
    @Query("""
            update Coupon c
            set c.issuedCount = c.issuedCount + 1
            where c.id = :couponId and c.issuedCount < c.maxIssuance
            """)
    int claimIssuanceSlot(@Param("couponId") UUID couponId);
}

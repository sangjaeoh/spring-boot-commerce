package com.commerce.coupon.repository;

import com.commerce.coupon.entity.Coupon;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /** 쿠폰 정책 페이지를 최신 등록순으로 조회한다. */
    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
    @Query("select c from Coupon c order by c.id desc")
    Page<Coupon> findPage(Pageable pageable);

    /** 발급 가능한(활성·기간 내·한도 미소진) 쿠폰 정책 페이지를 최신 등록순으로 조회한다. */
    // UUIDv7이 시간순이라 id desc가 최신 등록순이다.
    @Query("""
            select c from Coupon c
            where c.status = com.commerce.coupon.entity.CouponStatus.ACTIVE
                and c.validity.validFrom <= :now and c.validity.validUntil >= :now
                and (c.maxIssuance is null or c.issuedCount < c.maxIssuance)
            order by c.id desc
            """)
    Page<Coupon> findIssuablePage(@Param("now") Instant now, Pageable pageable);

    /**
     * 발급 한도 내에서 발급 카운트를 원자적으로 선점하고 갱신 행 수를 반환한다. 0이면 한도 소진이다.
     *
     * <p>무제한 정책({@code maxIssuance == null})은 비교가 성립하지 않아 항상 0을 반환하므로 호출하지 않는다.
     */
    // 조건부 UPDATE가 행 락으로 동시 발급을 직렬화해야 경합에서도 한도 초과가 나지 않는다.
    @Modifying
    @Query("""
            update Coupon c
            set c.issuedCount = c.issuedCount + 1
            where c.id = :couponId and c.issuedCount < c.maxIssuance
            """)
    int claimIssuanceSlot(@Param("couponId") UUID couponId);
}

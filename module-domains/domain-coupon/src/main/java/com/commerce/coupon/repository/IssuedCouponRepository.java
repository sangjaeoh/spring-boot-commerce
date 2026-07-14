package com.commerce.coupon.repository;

import com.commerce.coupon.entity.IssuedCoupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, UUID> {

    Optional<IssuedCoupon> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByCouponIdAndMemberId(UUID couponId, UUID memberId);
}

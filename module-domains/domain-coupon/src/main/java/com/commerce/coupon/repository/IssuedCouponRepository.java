package com.commerce.coupon.repository;

import com.commerce.coupon.entity.IssuedCoupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, UUID> {

    Optional<IssuedCoupon> findByIdAndMemberId(UUID id, UUID memberId);

    List<IssuedCoupon> findByMemberIdOrderByCreatedAtDescIdDesc(UUID memberId);

    Page<IssuedCoupon> findByCouponIdOrderByCreatedAtDescIdDesc(UUID couponId, Pageable pageable);

    boolean existsByCouponIdAndMemberId(UUID couponId, UUID memberId);
}

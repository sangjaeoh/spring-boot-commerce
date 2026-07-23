package com.commerce.domain.coupon.application.required;

import com.commerce.domain.coupon.domain.IssuedCoupon;
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

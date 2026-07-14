package com.commerce.coupon.repository;

import com.commerce.coupon.entity.Coupon;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {}

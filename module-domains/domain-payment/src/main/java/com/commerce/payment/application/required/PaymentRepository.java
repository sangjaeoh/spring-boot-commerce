package com.commerce.payment.application.required;

import com.commerce.payment.domain.Payment;
import com.commerce.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);
}

package com.commerce.domain.payment.application.required;

import com.commerce.domain.payment.domain.PaymentRefund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    boolean existsByRefundKey(UUID refundKey);

    List<PaymentRefund> findAllByPaymentId(UUID paymentId);
}

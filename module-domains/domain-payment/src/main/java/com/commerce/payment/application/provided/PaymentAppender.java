package com.commerce.payment.application.provided;

import com.commerce.payment.domain.PaymentMethod;
import com.commerce.payment.domain.exception.DuplicatePaymentException;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 결제 요청 생성을 담당하는 서비스다. */
public interface PaymentAppender {

    /**
     * 주문에 결제를 요청하고 새 결제 ID를 반환한다.
     *
     * @throws DuplicatePaymentException 이미 결제가 있는 주문이면
     */
    UUID request(UUID orderId, Money amount, @Nullable PaymentMethod method);
}

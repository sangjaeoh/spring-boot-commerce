package com.commerce.domain.payment.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.shared.entity.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 부분 환불 거래 기록. 부분 환불 1건당 1행 — PG 취소 거래 ID로 건별 추적하고,
 * refund_key(주문 라인 ID) 유니크로 재시도 중복 기록을 막는다.
 */
@Entity
@Table(schema = "payment", name = "payment_refund")
public class PaymentRefund extends BaseTimeEntity<UUID> {

    /** 환불 기록 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 환불 대상 결제 식별자. payment 도메인 논리 참조. */
    @Column(name = "payment_id")
    private UUID paymentId;

    /** 이번 부분 환불분 금액. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount")
    private Money amount;

    /** PG 취소 거래 ID. 무 PG 승인·0원 환불은 PG 호출이 없어 비어 있다. */
    @Nullable
    @Column(name = "pg_cancel_transaction_id")
    private String pgCancelTransactionId;

    /** 라인 멱등 키(주문 라인 ID). 한 라인당 부분 환불 1건을 식별한다. */
    @Column(name = "refund_key")
    private UUID refundKey;

    protected PaymentRefund() {}

    private PaymentRefund(
            UUID id, UUID paymentId, Money amount, @Nullable String pgCancelTransactionId, UUID refundKey) {
        this.id = id;
        this.paymentId = paymentId;
        this.amount = amount;
        this.pgCancelTransactionId = pgCancelTransactionId;
        this.refundKey = refundKey;
    }

    public static PaymentRefund record(
            UUID paymentId, Money amount, @Nullable String pgCancelTransactionId, UUID refundKey) {
        return new PaymentRefund(UuidV7Generator.generate(), paymentId, amount, pgCancelTransactionId, refundKey);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Money getAmount() {
        return amount;
    }

    public @Nullable String getPgCancelTransactionId() {
        return pgCancelTransactionId;
    }

    public UUID getRefundKey() {
        return refundKey;
    }
}

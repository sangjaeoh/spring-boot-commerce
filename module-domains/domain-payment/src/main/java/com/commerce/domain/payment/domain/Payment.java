package com.commerce.domain.payment.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.payment.domain.exception.PaymentErrorCode;
import com.commerce.domain.payment.domain.exception.PaymentStatusException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.shared.entity.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 결제 애그리거트 루트다. 주문당 한 행이며 결제 금액·수단과 PG 거래 결과를 소유한다. */
@Entity
@Table(schema = "payment", name = "payment")
public class Payment extends BaseTimeEntity<UUID> {

    /** 결제 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 결제 대상 주문 식별자. order 도메인 논리 참조. 주문당 유니크하다. */
    @Column(name = "order_id")
    private UUID orderId;

    /** 결제 금액. 주문 실청구액과 같다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount")
    private Money amount;

    /** 부분 취소로 환불된 누계. 결제액에 도달하면 취소로 전이한다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "refunded_amount")
    private Money refundedAmount;

    /** 결제 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    /** 결제 수단. 결제 금액이 0보다 클 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    @Nullable
    private PaymentMethod method;

    /** 승인 실패 사유. 실패 상태일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    @Nullable
    private FailureReason failureReason;

    /** PG 승인 거래 ID. PG를 호출한 승인에만 있다. */
    @Column(name = "pg_transaction_id")
    @Nullable
    private String pgTransactionId;

    /** PG 취소(환불) 거래 ID. PG 환불을 호출한 취소에만 있다. */
    @Column(name = "pg_cancel_transaction_id")
    @Nullable
    private String pgCancelTransactionId;

    /** 승인 시각. */
    @Column(name = "approved_at")
    @Nullable
    private Instant approvedAt;

    /** 취소·환불 시각. */
    @Column(name = "cancelled_at")
    @Nullable
    private Instant cancelledAt;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    protected Payment() {}

    private Payment(UUID id, UUID orderId, Money amount, @Nullable PaymentMethod method) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.REQUESTED;
        this.refundedAmount = Money.ZERO;
    }

    /** 결제를 요청 상태로 생성한다. */
    public static Payment request(UUID orderId, Money amount, @Nullable PaymentMethod method) {
        boolean hasAmount = !amount.isZero();
        boolean hasMethod = method != null;
        if (hasAmount != hasMethod) {
            throw new IllegalArgumentException("결제 금액과 수단의 존재가 일치해야 한다");
        }
        return new Payment(UuidV7Generator.generate(), orderId, amount, method);
    }

    /**
     * PG 승인 성공을 반영한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public void approve(String pgTransactionId, Instant now) {
        requireRequested();
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = now;
    }

    /**
     * PG를 생략하고 자동 승인한다(전액 할인).
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public void approveWithoutGateway(Instant now) {
        requireRequested();
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
    }

    /**
     * PG 승인 실패를 반영한다.
     *
     * @throws PaymentStatusException 요청 상태가 아니면
     */
    public void fail(FailureReason failureReason) {
        requireRequested();
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    /**
     * 결제를 취소·환불한다.
     *
     * @throws PaymentStatusException 승인 상태가 아니면
     */
    public void cancel(@Nullable String pgCancelTransactionId, Instant now) {
        if (status != PaymentStatus.APPROVED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        // 부분 환불 이력이 있으면 전액 PG 취소가 이중 환불이 된다 — 라인 단위로 마저 취소한다.
        if (!refundedAmount.isZero()) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        this.status = PaymentStatus.CANCELLED;
        this.pgCancelTransactionId = pgCancelTransactionId;
        this.cancelledAt = now;
    }

    /**
     * 부분 환불 누계를 주문 측 확정 누계로 단조 동기화한다. 같은·낮은 누계의 재동기화는 이중 반영 없이
     * 통과하고(재개·중복 전달 멱등), 누계가 결제액에 도달하면 취소로 전이하며, 종결 후 재동기화는 멱등 통과한다.
     *
     * @throws PaymentStatusException 승인·취소 상태가 아니거나 누계가 결제액을 초과하면
     */
    public void syncPartialRefund(Money cumulativeTotal, Instant now) {
        if (amount.isLessThan(cumulativeTotal)) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        if (status == PaymentStatus.CANCELLED) {
            return;
        }
        if (status != PaymentStatus.APPROVED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
        if (refundedAmount.isLessThan(cumulativeTotal)) {
            this.refundedAmount = cumulativeTotal;
        }
        if (refundedAmount.isGreaterThanOrEqualTo(amount)) {
            this.status = PaymentStatus.CANCELLED;
            this.cancelledAt = now;
        }
    }

    /** PG 승인이 필요한지(결제 금액이 0보다 큰지) 본다. */
    public boolean requiresGatewayApproval() {
        return !amount.isZero();
    }

    /** PG 호출용 결제 수단을 반환한다. {@code amount > 0}이면 불변식상 존재한다. */
    public PaymentMethod requireMethod() {
        return Objects.requireNonNull(method);
    }

    /** 요청 상태가 아니면 거부한다. */
    private void requireRequested() {
        if (status != PaymentStatus.REQUESTED) {
            throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
        }
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public @Nullable PaymentMethod getMethod() {
        return method;
    }

    public @Nullable FailureReason getFailureReason() {
        return failureReason;
    }

    public @Nullable String getPgTransactionId() {
        return pgTransactionId;
    }

    public @Nullable String getPgCancelTransactionId() {
        return pgCancelTransactionId;
    }

    public @Nullable Instant getApprovedAt() {
        return approvedAt;
    }

    public Money getRefundedAmount() {
        return refundedAmount;
    }

    public @Nullable Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}

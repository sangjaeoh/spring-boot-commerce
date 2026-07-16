package com.commerce.payment.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.payment.exception.PaymentErrorCode;
import com.commerce.payment.exception.PaymentStatusException;
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

/**
 * 결제 애그리거트 루트다. 주문당 1행이지만 취소(환불) 전이에 사용자 취소·리컨실·웹훅 확정이 겹칠 수 있어
 * 동시 중복 전이를 낙관락({@code @Version})으로 직렬화한다 — 겹친 취소 2건이 모두 가드를 통과해도 한쪽만
 * 커밋된다.
 *
 * <p>불변식 {@code amount > 0 ⇔ method != null}. 전액 할인(amount == 0)은 PG를 생략하고 자동 승인한다.
 */
@Entity
@Table(schema = "payment", name = "payment")
public class Payment extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount")
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    @Nullable
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    @Nullable
    private FailureReason failureReason;

    @Column(name = "pg_transaction_id")
    @Nullable
    private String pgTransactionId;

    @Column(name = "pg_cancel_transaction_id")
    @Nullable
    private String pgCancelTransactionId;

    @Column(name = "approved_at")
    @Nullable
    private Instant approvedAt;

    @Column(name = "cancelled_at")
    @Nullable
    private Instant cancelledAt;

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

    /** PG 승인 성공을 반영한다. */
    public void approve(String pgTransactionId, Instant now) {
        requireRequested();
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = now;
    }

    /** PG를 생략하고 자동 승인한다(전액 할인). */
    public void approveWithoutGateway(Instant now) {
        requireRequested();
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = now;
    }

    /** PG 승인 실패를 반영한다. */
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
        this.status = PaymentStatus.CANCELLED;
        this.pgCancelTransactionId = pgCancelTransactionId;
        this.cancelledAt = now;
    }

    /** PG 승인이 필요한지(금액 > 0) 여부다. */
    public boolean requiresGatewayApproval() {
        return !amount.isZero();
    }

    /** PG 호출용 결제 수단을 반환한다. {@code amount > 0}이면 불변식상 존재한다. */
    public PaymentMethod requireMethod() {
        return Objects.requireNonNull(method);
    }

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

    public @Nullable Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}

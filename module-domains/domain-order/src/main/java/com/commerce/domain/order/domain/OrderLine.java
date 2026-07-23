package com.commerce.domain.order.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.shared.entity.MoneyConverter;
import com.google.errorprone.annotations.Keep;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 라인이다. 주문 시점의 변형·상품명·옵션·단가를 스냅샷으로 보관해 자기완결적이다. */
@Entity
@Table(schema = "ordering", name = "order_line")
public class OrderLine extends BaseTimeEntity<UUID> {

    /** 주문 라인 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 부모 주문. */
    // Hibernate가 리플렉션으로 읽으나 자바 코드에선 읽지 않는다.
    @Keep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    /** 주문 시점 변형 식별자. product 도메인 논리 참조. */
    @Column(name = "variant_id")
    private UUID variantId;

    /** 주문 시점 상품 식별자. product 도메인 논리 참조. */
    @Column(name = "product_id")
    private UUID productId;

    /** 주문 시점 상품명 스냅샷. */
    @Column(name = "product_name")
    private String productName;

    /** 주문 시점 옵션 표시명 스냅샷. 표시명 없는 변형이면 없다. */
    @Column(name = "option_label")
    @Nullable
    private String optionLabel;

    /** 주문 시점 단가 스냅샷. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price")
    private Money unitPrice;

    /** 주문 수량. 1 이상. */
    @Column(name = "quantity")
    private int quantity;

    /** 취소 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderLineStatus status;

    /** 확정된 라인 환불액. 취소·반품 진행 이후 라인만 있다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "refund_amount")
    @Nullable
    private Money refundAmount;

    /** 라인 반품 사유. 반품 요청 이후 라인만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason")
    @Nullable
    private RefundReason returnReason;

    protected OrderLine() {}

    private OrderLine(UUID id, Order order, OrderLineSnapshot snapshot) {
        this.id = id;
        this.order = order;
        this.variantId = snapshot.variantId();
        this.productId = snapshot.productId();
        this.productName = snapshot.productName();
        this.optionLabel = snapshot.optionLabel();
        this.unitPrice = snapshot.unitPrice();
        this.quantity = snapshot.quantity();
        this.status = OrderLineStatus.ORDERED;
    }

    /** 부모 주문에 속한 라인을 스냅샷으로 만든다. */
    static OrderLine create(Order order, OrderLineSnapshot snapshot) {
        if (snapshot.quantity() < 1) {
            throw new IllegalArgumentException("라인 수량은 1 이상이어야 한다: " + snapshot.quantity());
        }
        return new OrderLine(UuidV7Generator.generate(), order, snapshot);
    }

    /** 단가에 수량을 곱해 라인 금액을 계산한다. */
    Money lineAmount() {
        return unitPrice.multiply(quantity);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public @Nullable String getOptionLabel() {
        return optionLabel;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderLineStatus getStatus() {
        return status;
    }

    public @Nullable Money getRefundAmount() {
        return refundAmount;
    }

    /** 환불액을 확정하고 취소 진행 중으로 둔다. */
    void markCancelling(Money refundAmount) {
        this.status = OrderLineStatus.CANCELLING;
        this.refundAmount = refundAmount;
    }

    /** 취소 진행 중 라인을 취소로 완결한다. */
    void completeCancellation() {
        this.status = OrderLineStatus.CANCELLED;
    }

    public @Nullable RefundReason getReturnReason() {
        return returnReason;
    }

    /** 반품을 요청 상태로 두고 사유를 기록한다. */
    void requestReturn(RefundReason reason) {
        this.status = OrderLineStatus.RETURN_REQUESTED;
        this.returnReason = reason;
    }

    /** 반품 요청을 거절해 주문됨으로 되돌리고 사유를 지운다. */
    void rejectReturn() {
        this.status = OrderLineStatus.ORDERED;
        this.returnReason = null;
    }

    /** 환불액을 확정하고 반품 진행 중으로 둔다. */
    void markReturning(Money refundAmount) {
        this.status = OrderLineStatus.RETURNING;
        this.refundAmount = refundAmount;
    }

    /** 반품 진행 중 라인을 반품으로 완결한다. */
    void completeReturn() {
        this.status = OrderLineStatus.RETURNED;
    }
}

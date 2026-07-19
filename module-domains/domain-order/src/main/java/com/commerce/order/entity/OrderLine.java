package com.commerce.order.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.google.errorprone.annotations.Keep;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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

    /** 부모 주문. {@code Order.lines}의 mappedBy 대상이자 컬럼 {@code order_id} 소유 필드다. */
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
}

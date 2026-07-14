package com.commerce.order.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
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

/**
 * 주문 라인이다. 주문 시점의 변형·상품명·옵션·단가를 스냅샷으로 보관해 자기완결적이다.
 *
 * <p>생성은 부모 {@link Order}를 통해서만 한다.
 */
@Entity
@Table(schema = "ordering", name = "order_line")
public class OrderLine extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    // Order.lines의 mappedBy 대상이자 FK(order_id) 소유 필드다. Hibernate가 리플렉션으로 읽으나 자바 코드에선 읽지 않는다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "option_label")
    @Nullable
    private String optionLabel;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price")
    private Money unitPrice;

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

    static OrderLine create(Order order, OrderLineSnapshot snapshot) {
        if (snapshot.quantity() < 1) {
            throw new IllegalArgumentException("라인 수량은 1 이상이어야 한다: " + snapshot.quantity());
        }
        return new OrderLine(UuidV7Generator.generate(), order, snapshot);
    }

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

package com.commerce.product.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.core.money.Money;
import com.commerce.jpa.converter.MoneyConverter;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.product.exception.InvalidVariantException;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductVariantStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 변형(판매·재고 단위) 애그리거트 루트다. 소속 상품은 {@code productId}로 참조한다.
 *
 * <p>최초 상태는 {@code DISABLED}이며 재고 시딩 후 {@code enable()}로 판매 제공한다. 옵션 조합은
 * 생성 시 확정·불변이다.
 */
@Entity
@Table(schema = "product", name = "product_variant")
public class ProductVariant extends BaseTimeEntity<UUID> {

    private static final Money MIN_PRICE = Money.of(1L);

    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "price")
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductVariantStatus status;

    @Column(name = "option_signature")
    private String optionSignature;

    @Column(name = "option_label")
    @Nullable
    private String optionLabel;

    protected ProductVariant() {}

    private ProductVariant(UUID id, UUID productId, Money price, NormalizedOptions options) {
        this.id = id;
        this.productId = productId;
        this.price = price;
        this.status = ProductVariantStatus.DISABLED;
        this.optionSignature = options.signature();
        this.optionLabel = options.label();
    }

    /** 비활성({@code DISABLED}) 상태로 변형을 생성한다. */
    public static ProductVariant create(UUID productId, Money price, NormalizedOptions options) {
        requireMinimumPrice(price);
        return new ProductVariant(UuidV7Generator.generate(), productId, price, options);
    }

    /** 변형을 판매 제공한다. */
    public void enable() {
        if (status != ProductVariantStatus.DISABLED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.ACTIVE;
    }

    /** 변형 판매 제공을 중단한다. */
    public void disable() {
        if (status != ProductVariantStatus.ACTIVE) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.DISABLED;
    }

    /** 변형을 은퇴시킨다. */
    public void retire() {
        if (status == ProductVariantStatus.RETIRED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.RETIRED;
    }

    /** 판매가를 바꾼다. */
    public void changePrice(Money newPrice) {
        if (status == ProductVariantStatus.RETIRED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        requireMinimumPrice(newPrice);
        this.price = newPrice;
    }

    private static void requireMinimumPrice(Money price) {
        if (price.isLessThan(MIN_PRICE)) {
            throw new InvalidVariantException(ProductErrorCode.INVALID_PRICE);
        }
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getProductId() {
        return productId;
    }

    public Money getPrice() {
        return price;
    }

    public ProductVariantStatus getStatus() {
        return status;
    }

    public String getOptionSignature() {
        return optionSignature;
    }

    public @Nullable String getOptionLabel() {
        return optionLabel;
    }
}

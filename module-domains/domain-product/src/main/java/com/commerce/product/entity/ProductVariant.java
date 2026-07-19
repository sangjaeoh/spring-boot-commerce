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

/** 상품 변형(variant) 애그리거트 루트다. 판매가와 옵션 조합을 소유하는 판매·재고 단위다. */
@Entity
@Table(schema = "product", name = "product_variant")
public class ProductVariant extends BaseTimeEntity<UUID> {

    private static final Money MIN_PRICE = Money.of(1L);

    /** 변형 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 소속 상품 식별자. 애그리거트 루트 간 ID 참조. */
    @Column(name = "product_id")
    private UUID productId;

    /** 변형 판매가. 1원 이상. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "price")
    private Money price;

    /** 카탈로그 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductVariantStatus status;

    /** 옵션 조합 정규화 키. 옵션명 기준 정렬·케이스 폴딩해 대조에 쓰는 값이며, 옵션이 없으면 {@code ""}. */
    @Column(name = "option_signature")
    private String optionSignature;

    /**
     * 옵션 조합 표시 라벨({@code Red / L}). 값을 입력 순서·대소문자 그대로 조인해 화면에 쓰는 값이며,
     * 옵션이 없는 변형에는 없다.
     */
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

    /**
     * 비활성({@code DISABLED}) 상태로 변형을 생성한다.
     *
     * @throws InvalidVariantException 판매가가 최소가(1원) 미만이면
     */
    public static ProductVariant create(UUID productId, Money price, NormalizedOptions options) {
        requireMinimumPrice(price);
        return new ProductVariant(UuidV7Generator.generate(), productId, price, options);
    }

    /**
     * 변형을 판매 제공한다.
     *
     * @throws ProductVariantStatusException 비활성 상태가 아니면
     */
    public void enable() {
        if (status != ProductVariantStatus.DISABLED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.ACTIVE;
    }

    /**
     * 변형 판매 제공을 중단한다.
     *
     * @throws ProductVariantStatusException 판매 제공 상태가 아니면
     */
    public void disable() {
        if (status != ProductVariantStatus.ACTIVE) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.DISABLED;
    }

    /**
     * 변형을 은퇴시킨다.
     *
     * @throws ProductVariantStatusException 이미 은퇴한 변형이면
     */
    public void retire() {
        if (status == ProductVariantStatus.RETIRED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        this.status = ProductVariantStatus.RETIRED;
    }

    /**
     * 판매가를 바꾼다.
     *
     * @throws ProductVariantStatusException 은퇴한 변형이면
     * @throws InvalidVariantException 판매가가 최소가(1원) 미만이면
     */
    public void changePrice(Money newPrice) {
        if (status == ProductVariantStatus.RETIRED) {
            throw new ProductVariantStatusException(ProductErrorCode.INVALID_VARIANT_STATE_TRANSITION);
        }
        requireMinimumPrice(newPrice);
        this.price = newPrice;
    }

    /** 판매가가 최소가 미만이면 거부한다. */
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

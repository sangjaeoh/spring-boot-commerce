package com.commerce.product.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import com.commerce.product.exception.ProductErrorCode;
import com.commerce.product.exception.ProductStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 상품 카탈로그 그룹 애그리거트 루트다. 판매가는 변형이 소유하므로 여기 두지 않는다.
 *
 * <p>최초 상태는 {@code HIDDEN}이며 변형·재고 시딩 후 {@code show()}로 노출한다.
 */
@Entity
@Table(schema = "product", name = "product")
public class Product extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    @Nullable
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status;

    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    protected Product() {}

    private Product(UUID id, String name, @Nullable String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = ProductStatus.HIDDEN;
    }

    /** 숨김({@code HIDDEN}) 상태로 상품을 등록한다. */
    public static Product create(String name, @Nullable String description) {
        return new Product(UuidV7Generator.generate(), name, description);
    }

    /** 상품을 노출한다. */
    public void show() {
        if (status != ProductStatus.HIDDEN) {
            throw new ProductStatusException(ProductErrorCode.INVALID_PRODUCT_STATE_TRANSITION);
        }
        this.status = ProductStatus.ON_SALE;
    }

    /** 상품을 숨긴다. */
    public void hide() {
        if (status != ProductStatus.ON_SALE) {
            throw new ProductStatusException(ProductErrorCode.INVALID_PRODUCT_STATE_TRANSITION);
        }
        this.status = ProductStatus.HIDDEN;
    }

    /** 상품명을 바꾼다. */
    public void rename(String newName) {
        this.name = newName;
    }

    /** 상세 설명을 바꾼다. */
    public void changeDescription(@Nullable String newDescription) {
        this.description = newDescription;
    }

    /** 상품을 논리삭제한다. */
    public void delete() {
        this.deletedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }
}

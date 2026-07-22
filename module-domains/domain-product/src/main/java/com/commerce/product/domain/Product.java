package com.commerce.product.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 카탈로그 그룹 애그리거트 루트다. */
@Entity
@Table(schema = "product", name = "product")
public class Product extends BaseTimeEntity<UUID> {

    /** 상품 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 상품명. */
    @Column(name = "name")
    private String name;

    /** 상세 설명. */
    @Column(name = "description")
    @Nullable
    private String description;

    /** 카탈로그 노출 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status;

    /** 소속 카테고리 식별자. 애그리거트 루트 간 ID 참조이며 미분류면 없다. */
    @Column(name = "category_id")
    @Nullable
    private UUID categoryId;

    /** 논리삭제 시각. 삭제 전이면 없다. */
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

    /**
     * 상품을 노출한다.
     *
     * @throws ProductStatusException 숨김 상태가 아니면
     */
    public void show() {
        if (status != ProductStatus.HIDDEN) {
            throw new ProductStatusException(ProductErrorCode.INVALID_PRODUCT_STATE_TRANSITION);
        }
        this.status = ProductStatus.ON_SALE;
    }

    /**
     * 상품을 숨긴다.
     *
     * @throws ProductStatusException 노출 상태가 아니면
     */
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

    /** 카테고리를 지정한다. null이면 미분류로 해제한다. */
    public void assignCategory(@Nullable UUID categoryId) {
        this.categoryId = categoryId;
    }

    /** 상품을 논리삭제한다. */
    public void delete(Instant now) {
        this.deletedAt = now;
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

    public @Nullable UUID getCategoryId() {
        return categoryId;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }
}

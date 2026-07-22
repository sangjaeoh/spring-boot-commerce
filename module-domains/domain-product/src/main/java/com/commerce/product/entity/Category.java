package com.commerce.product.entity;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 상품 분류(category) 애그리거트 루트다. 계층 없는 단일 레벨이다. */
@Entity
@Table(schema = "product", name = "category")
public class Category extends BaseTimeEntity<UUID> {

    /** 카테고리 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 카테고리 이름. */
    @Column(name = "name")
    private String name;

    /** 논리삭제 시각. 삭제 전이면 없다. */
    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    protected Category() {}

    private Category(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** 카테고리를 생성한다. */
    public static Category create(String name) {
        return new Category(UuidV7Generator.generate(), name);
    }

    /** 카테고리 이름을 바꾼다. */
    public void rename(String newName) {
        this.name = newName;
    }

    /** 카테고리를 논리삭제한다. */
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

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }
}

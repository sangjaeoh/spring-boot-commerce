package com.commerce.product.domain;

import com.commerce.core.id.UuidV7Generator;
import com.commerce.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 상품 이미지 애그리거트 루트다. 바이트는 스토리지가, 메타(키·URL·정렬 순서)는 이 엔티티가 소유한다. */
@Entity
@Table(schema = "product", name = "product_image")
public class ProductImage extends BaseTimeEntity<UUID> {

    /** 이미지 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 소속 상품 식별자. 애그리거트 루트 간 ID 참조. */
    @Column(name = "product_id")
    private UUID productId;

    /** 스토리지 보관 키. 삭제 시 스토리지 제거에 쓴다. */
    @Column(name = "storage_key")
    private String storageKey;

    /** 공개 URL. 업로드 시점에 스토리지가 반환한 값. */
    @Column(name = "url")
    private String url;

    /** 정렬 순서. 0부터 업로드 순이고 최솟값이 대표 이미지다. */
    @Column(name = "sort_order")
    private int sortOrder;

    protected ProductImage() {}

    private ProductImage(UUID id, UUID productId, String storageKey, String url, int sortOrder) {
        this.id = id;
        this.productId = productId;
        this.storageKey = storageKey;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    /** 상품 이미지를 생성한다. */
    public static ProductImage create(UUID productId, String storageKey, String url, int sortOrder) {
        return new ProductImage(UuidV7Generator.generate(), productId, storageKey, url, sortOrder);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getUrl() {
        return url;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}

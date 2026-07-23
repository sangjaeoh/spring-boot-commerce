package com.commerce.domain.wishlist.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 회원이 찜한 상품 한 건이다. (회원, 상품) 유니크로 회원의 위시리스트를 이룬다. */
@Entity
@Table(schema = "wishlist", name = "wishlist_item")
public class WishlistItem extends BaseTimeEntity<UUID> {

    /** 찜 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 찜한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 찜한 상품 식별자. product 도메인 논리 참조. */
    @Column(name = "product_id")
    private UUID productId;

    protected WishlistItem() {}

    private WishlistItem(UUID id, UUID memberId, UUID productId) {
        this.id = id;
        this.memberId = memberId;
        this.productId = productId;
    }

    /** 찜을 생성한다. */
    public static WishlistItem create(UUID memberId, UUID productId) {
        return new WishlistItem(UuidV7Generator.generate(), memberId, productId);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getProductId() {
        return productId;
    }
}

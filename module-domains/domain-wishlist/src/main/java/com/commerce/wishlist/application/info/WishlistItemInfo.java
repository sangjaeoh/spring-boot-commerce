package com.commerce.wishlist.application.info;

import com.commerce.wishlist.domain.WishlistItem;
import java.time.Instant;
import java.util.UUID;

/** 찜 한 건의 조회 경계 모델이다. */
public record WishlistItemInfo(UUID productId, Instant wishedAt) {

    /** 찜 엔티티에서 조회 모델을 만든다. */
    public static WishlistItemInfo from(WishlistItem item) {
        return new WishlistItemInfo(item.getProductId(), item.getCreatedAt());
    }
}

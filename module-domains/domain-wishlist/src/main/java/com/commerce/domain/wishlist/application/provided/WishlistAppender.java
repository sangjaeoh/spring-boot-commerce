package com.commerce.domain.wishlist.application.provided;

import java.util.UUID;

/** 찜 추가를 담당하는 서비스다. */
public interface WishlistAppender {

    /** 상품을 찜한다. 이미 찜한 상품이면 아무 일도 하지 않는다(멱등). */
    void add(UUID memberId, UUID productId);
}

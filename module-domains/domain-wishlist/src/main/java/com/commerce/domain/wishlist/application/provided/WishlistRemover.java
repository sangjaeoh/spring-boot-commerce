package com.commerce.domain.wishlist.application.provided;

import java.util.UUID;

/** 찜 삭제를 담당하는 서비스다. */
public interface WishlistRemover {

    /** 찜을 해제한다. 찜하지 않은 상품이면 아무 일도 하지 않는다(멱등). */
    void remove(UUID memberId, UUID productId);
}

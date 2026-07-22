package com.commerce.wishlist.application;

import com.commerce.wishlist.application.required.WishlistItemRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 찜 삭제를 담당하는 서비스다. */
@Service
public class WishlistRemover {

    private final WishlistItemRepository wishlistItemRepository;

    public WishlistRemover(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    /** 찜을 해제한다. 찜하지 않은 상품이면 아무 일도 하지 않는다(멱등). */
    @Transactional
    public void remove(UUID memberId, UUID productId) {
        wishlistItemRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}

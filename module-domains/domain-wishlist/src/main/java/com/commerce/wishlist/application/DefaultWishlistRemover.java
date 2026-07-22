package com.commerce.wishlist.application;

import com.commerce.wishlist.application.provided.WishlistRemover;
import com.commerce.wishlist.application.required.WishlistItemRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link WishlistRemover}의 기본 구현이다. */
@Service
class DefaultWishlistRemover implements WishlistRemover {

    private final WishlistItemRepository wishlistItemRepository;

    DefaultWishlistRemover(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional
    @Override
    public void remove(UUID memberId, UUID productId) {
        wishlistItemRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}

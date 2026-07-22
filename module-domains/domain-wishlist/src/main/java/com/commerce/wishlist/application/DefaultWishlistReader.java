package com.commerce.wishlist.application;

import com.commerce.wishlist.application.info.WishlistItemInfo;
import com.commerce.wishlist.application.provided.WishlistReader;
import com.commerce.wishlist.application.required.WishlistItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link WishlistReader}의 기본 구현이다. */
@Service
class DefaultWishlistReader implements WishlistReader {

    private final WishlistItemRepository wishlistItemRepository;

    DefaultWishlistReader(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<WishlistItemInfo> getWishlist(UUID memberId) {
        return wishlistItemRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
                .map(WishlistItemInfo::from)
                .toList();
    }
}

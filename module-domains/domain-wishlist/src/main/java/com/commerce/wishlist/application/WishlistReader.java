package com.commerce.wishlist.application;

import com.commerce.wishlist.application.info.WishlistItemInfo;
import com.commerce.wishlist.application.required.WishlistItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 찜 목록 조회를 담당하는 서비스다. */
@Service
public class WishlistReader {

    private final WishlistItemRepository wishlistItemRepository;

    public WishlistReader(WishlistItemRepository wishlistItemRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
    }

    /** 회원의 찜 목록을 최신 찜 우선으로 조회한다. */
    @Transactional(readOnly = true)
    public List<WishlistItemInfo> getWishlist(UUID memberId) {
        return wishlistItemRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
                .map(WishlistItemInfo::from)
                .toList();
    }
}

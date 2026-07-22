package com.commerce.wishlist.application.provided;

import com.commerce.wishlist.application.info.WishlistItemInfo;
import java.util.List;
import java.util.UUID;

/** 찜 목록 조회를 담당하는 서비스다. */
public interface WishlistReader {

    /** 회원의 찜 목록을 최신 찜 우선으로 조회한다. */
    List<WishlistItemInfo> getWishlist(UUID memberId);
}

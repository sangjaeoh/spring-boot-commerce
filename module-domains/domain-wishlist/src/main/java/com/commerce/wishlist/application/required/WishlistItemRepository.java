package com.commerce.wishlist.application.required;

import com.commerce.wishlist.domain.WishlistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    boolean existsByMemberIdAndProductId(UUID memberId, UUID productId);

    // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 찜 우선 정렬을 겸한다.
    List<WishlistItem> findAllByMemberIdOrderByIdDesc(UUID memberId);

    void deleteByMemberIdAndProductId(UUID memberId, UUID productId);
}

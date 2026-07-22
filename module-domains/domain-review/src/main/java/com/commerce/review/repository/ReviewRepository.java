package com.commerce.review.repository;

import com.commerce.review.entity.Review;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByMemberIdAndProductId(UUID memberId, UUID productId);

    Optional<Review> findByIdAndMemberId(UUID id, UUID memberId);

    // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 리뷰 우선 정렬을 겸한다.
    Page<Review> findByProductIdOrderByIdDesc(UUID productId, Pageable pageable);
}

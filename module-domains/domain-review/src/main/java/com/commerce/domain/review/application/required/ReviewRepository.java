package com.commerce.domain.review.application.required;

import com.commerce.domain.review.domain.Review;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /** 활성 리뷰 또는 관리자 삭제 리뷰가 있는지 본다 — 본인 삭제(MEMBER)는 재작성을 막지 않는다. */
    @Query("select count(r) > 0 from Review r"
            + " where r.memberId = :memberId and r.productId = :productId"
            + " and (r.deletedAt is null or r.deletedBy = com.commerce.domain.review.domain.DeletedBy.ADMIN)")
    boolean existsActiveOrAdminRemoved(@Param("memberId") UUID memberId, @Param("productId") UUID productId);

    Optional<Review> findByIdAndMemberIdAndDeletedAtIsNull(UUID id, UUID memberId);

    Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

    // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 리뷰 우선 정렬을 겸한다.
    Page<Review> findByProductIdAndDeletedAtIsNullOrderByIdDesc(UUID productId, Pageable pageable);
}

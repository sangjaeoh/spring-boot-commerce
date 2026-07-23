package com.commerce.domain.review.application.info;

import com.commerce.domain.review.domain.Review;
import java.time.Instant;
import java.util.UUID;

/** 리뷰 한 건의 조회 경계 모델이다. */
public record ReviewInfo(UUID id, UUID productId, int rating, String content, Instant writtenAt) {

    /** 리뷰 엔티티에서 조회 모델을 만든다. */
    public static ReviewInfo from(Review review) {
        return new ReviewInfo(
                review.getId(), review.getProductId(), review.getRating(), review.getContent(), review.getCreatedAt());
    }
}

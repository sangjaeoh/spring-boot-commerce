package com.commerce.review.application;

import com.commerce.review.application.provided.ReviewRemover;
import com.commerce.review.application.required.ReviewRepository;
import com.commerce.review.domain.ReviewErrorCode;
import com.commerce.review.domain.ReviewNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ReviewRemover}의 기본 구현이다. */
@Service
class DefaultReviewRemover implements ReviewRemover {

    private final ReviewRepository reviewRepository;

    DefaultReviewRemover(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    @Override
    public void remove(UUID reviewId, UUID memberId) {
        reviewRepository.delete(reviewRepository
                .findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ReviewNotFoundException(ReviewErrorCode.REVIEW_NOT_FOUND)));
    }
}

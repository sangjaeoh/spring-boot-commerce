package com.commerce.domain.review.application;

import com.commerce.domain.review.application.provided.ReviewRemover;
import com.commerce.domain.review.application.required.ReviewRepository;
import com.commerce.domain.review.domain.exception.ReviewErrorCode;
import com.commerce.domain.review.domain.exception.ReviewNotFoundException;
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

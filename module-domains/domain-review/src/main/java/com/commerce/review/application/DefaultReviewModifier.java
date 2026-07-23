package com.commerce.review.application;

import com.commerce.review.application.provided.ReviewModifier;
import com.commerce.review.application.required.ReviewRepository;
import com.commerce.review.domain.exception.ReviewErrorCode;
import com.commerce.review.domain.exception.ReviewNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ReviewModifier}의 기본 구현이다. */
@Service
class DefaultReviewModifier implements ReviewModifier {

    private final ReviewRepository reviewRepository;

    DefaultReviewModifier(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    @Override
    public void revise(UUID reviewId, UUID memberId, int rating, String content) {
        reviewRepository
                .findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ReviewNotFoundException(ReviewErrorCode.REVIEW_NOT_FOUND))
                .revise(rating, content);
    }
}

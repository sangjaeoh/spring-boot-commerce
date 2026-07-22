package com.commerce.review.application;

import com.commerce.review.application.required.ReviewRepository;
import com.commerce.review.domain.ReviewErrorCode;
import com.commerce.review.domain.ReviewNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 삭제를 담당하는 서비스다. */
@Service
public class ReviewRemover {

    private final ReviewRepository reviewRepository;

    public ReviewRemover(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * 본인 리뷰를 삭제한다.
     *
     * @throws ReviewNotFoundException 본인 소유 리뷰가 없으면
     */
    @Transactional
    public void remove(UUID reviewId, UUID memberId) {
        reviewRepository.delete(reviewRepository
                .findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ReviewNotFoundException(ReviewErrorCode.REVIEW_NOT_FOUND)));
    }
}

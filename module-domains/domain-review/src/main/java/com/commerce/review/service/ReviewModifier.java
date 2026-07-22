package com.commerce.review.service;

import com.commerce.review.exception.InvalidReviewException;
import com.commerce.review.exception.ReviewErrorCode;
import com.commerce.review.exception.ReviewNotFoundException;
import com.commerce.review.repository.ReviewRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 수정을 담당하는 서비스다. */
@Service
public class ReviewModifier {

    private final ReviewRepository reviewRepository;

    public ReviewModifier(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * 본인 리뷰의 별점·본문을 고쳐 쓴다.
     *
     * @throws ReviewNotFoundException 본인 소유 리뷰가 없으면
     * @throws InvalidReviewException 별점·본문이 허용 범위를 벗어나면
     */
    @Transactional
    public void revise(UUID reviewId, UUID memberId, int rating, String content) {
        reviewRepository
                .findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ReviewNotFoundException(ReviewErrorCode.REVIEW_NOT_FOUND))
                .revise(rating, content);
    }
}

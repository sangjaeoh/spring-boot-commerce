package com.commerce.domain.review.application;

import com.commerce.domain.review.application.info.ReviewInfo;
import com.commerce.domain.review.application.provided.ReviewReader;
import com.commerce.domain.review.application.required.ReviewRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ReviewReader}의 기본 구현이다. */
@Service
class DefaultReviewReader implements ReviewReader {

    private final ReviewRepository reviewRepository;

    DefaultReviewReader(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ReviewInfo> getProductPage(UUID productId, Pageable pageable) {
        return reviewRepository
                .findByProductIdAndDeletedAtIsNullOrderByIdDesc(productId, pageable)
                .map(ReviewInfo::from);
    }
}

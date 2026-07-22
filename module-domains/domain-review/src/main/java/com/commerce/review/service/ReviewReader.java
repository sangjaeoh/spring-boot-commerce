package com.commerce.review.service;

import com.commerce.review.info.ReviewInfo;
import com.commerce.review.repository.ReviewRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 조회를 담당하는 서비스다. */
@Service
public class ReviewReader {

    private final ReviewRepository reviewRepository;

    public ReviewReader(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /** 상품의 리뷰 페이지를 최신 리뷰 우선으로 조회한다. */
    @Transactional(readOnly = true)
    public Page<ReviewInfo> getProductPage(UUID productId, Pageable pageable) {
        return reviewRepository
                .findByProductIdOrderByIdDesc(productId, pageable)
                .map(ReviewInfo::from);
    }
}

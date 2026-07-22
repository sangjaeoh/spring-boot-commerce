package com.commerce.review.application.provided;

import com.commerce.review.application.info.ReviewInfo;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 리뷰 조회를 담당하는 서비스다. */
public interface ReviewReader {

    /** 상품의 리뷰 페이지를 최신 리뷰 우선으로 조회한다. */
    Page<ReviewInfo> getProductPage(UUID productId, Pageable pageable);
}

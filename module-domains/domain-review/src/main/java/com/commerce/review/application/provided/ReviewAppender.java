package com.commerce.review.application.provided;

import com.commerce.review.domain.exception.DuplicateReviewException;
import com.commerce.review.domain.exception.InvalidReviewException;
import java.util.UUID;

/** 리뷰 작성을 담당하는 서비스다. */
public interface ReviewAppender {

    /**
     * 리뷰를 쓰고 리뷰 ID를 반환한다.
     *
     * @throws DuplicateReviewException 같은 상품에 이미 리뷰가 있으면
     * @throws InvalidReviewException 별점·본문이 허용 범위를 벗어나면
     */
    UUID write(UUID memberId, UUID productId, int rating, String content);
}

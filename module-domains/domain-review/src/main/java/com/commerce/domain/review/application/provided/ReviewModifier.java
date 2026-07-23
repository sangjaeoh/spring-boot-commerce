package com.commerce.domain.review.application.provided;

import com.commerce.domain.review.domain.exception.InvalidReviewException;
import com.commerce.domain.review.domain.exception.ReviewNotFoundException;
import java.util.UUID;

/** 리뷰 수정을 담당하는 서비스다. */
public interface ReviewModifier {

    /**
     * 본인 리뷰의 별점·본문을 고쳐 쓴다.
     *
     * @throws ReviewNotFoundException 본인 소유 리뷰가 없으면
     * @throws InvalidReviewException 별점·본문이 허용 범위를 벗어나면
     */
    void revise(UUID reviewId, UUID memberId, int rating, String content);
}

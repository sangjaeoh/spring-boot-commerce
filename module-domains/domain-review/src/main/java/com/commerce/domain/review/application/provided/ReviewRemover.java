package com.commerce.domain.review.application.provided;

import com.commerce.domain.review.domain.exception.ReviewNotFoundException;
import java.util.UUID;

/** 리뷰 삭제를 담당하는 서비스다. */
public interface ReviewRemover {

    /**
     * 본인 리뷰를 삭제한다.
     *
     * @throws ReviewNotFoundException 본인 소유 리뷰가 없으면
     */
    void remove(UUID reviewId, UUID memberId);
}

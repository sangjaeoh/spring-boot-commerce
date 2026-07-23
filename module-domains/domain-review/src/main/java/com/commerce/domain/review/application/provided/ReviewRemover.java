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

    /**
     * 관리자가 리뷰를 사유와 함께 제거한다. 제거된 리뷰는 공개 목록에서 빠지고 사유가 보존된다.
     *
     * @throws ReviewNotFoundException 리뷰가 없거나 이미 제거됐으면
     */
    void removeByAdmin(UUID reviewId, String reason);
}

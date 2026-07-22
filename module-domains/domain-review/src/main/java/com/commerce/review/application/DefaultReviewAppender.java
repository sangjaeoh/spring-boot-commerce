package com.commerce.review.application;

import com.commerce.review.application.provided.ReviewAppender;
import com.commerce.review.application.required.ReviewRepository;
import com.commerce.review.domain.DuplicateReviewException;
import com.commerce.review.domain.Review;
import com.commerce.review.domain.ReviewErrorCode;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link ReviewAppender}의 기본 구현이다. */
@Service
class DefaultReviewAppender implements ReviewAppender {

    private final ReviewRepository reviewRepository;
    private final TransactionTemplate transactionTemplate;

    DefaultReviewAppender(ReviewRepository reviewRepository, PlatformTransactionManager transactionManager) {
        this.reviewRepository = reviewRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // 유니크 위반을 중복 거부로 바꾸려면 시도가 독립 물리 트랜잭션이어야 한다. 외부 트랜잭션에 합류하면
        // 위반이 그 트랜잭션을 rollback-only로 오염시킨다.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public UUID write(UUID memberId, UUID productId, int rating, String content) {
        try {
            return writeOnce(memberId, productId, rating, content);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 작성 경합 — 상대가 먼저 썼으므로 사전 exists 검사와 같은 중복 거부로 수렴한다.
            throw new DuplicateReviewException(ReviewErrorCode.ALREADY_WRITTEN);
        }
    }

    /** 중복이 없으면 저장을 한 번 시도한다. */
    private UUID writeOnce(UUID memberId, UUID productId, int rating, String content) {
        UUID reviewId = transactionTemplate.execute(status -> {
            if (reviewRepository.existsByMemberIdAndProductId(memberId, productId)) {
                throw new DuplicateReviewException(ReviewErrorCode.ALREADY_WRITTEN);
            }
            Review review = reviewRepository.save(Review.create(memberId, productId, rating, content));
            // 유니크 위반을 이 시도의 트랜잭션 안에서 표면화해, 커밋 시점이 아니라 여기서 잡는다.
            reviewRepository.flush();
            return review.getId();
        });
        if (reviewId == null) {
            // execute 콜백이 항상 ID를 반환하므로 도달할 수 없는 서버 버그다.
            throw new IllegalStateException("리뷰 저장 트랜잭션이 ID를 반환하지 않았다");
        }
        return reviewId;
    }
}

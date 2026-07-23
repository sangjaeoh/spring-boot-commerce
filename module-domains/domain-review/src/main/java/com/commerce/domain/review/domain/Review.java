package com.commerce.domain.review.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.review.domain.exception.InvalidReviewException;
import com.commerce.domain.review.domain.exception.ReviewErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 회원이 구매확정한 상품에 쓴 리뷰다. (회원, 상품) 유니크로 상품당 한 건이다. */
@Entity
@Table(schema = "review", name = "review")
public class Review extends BaseTimeEntity<UUID> {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 1000;

    /** 리뷰 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 작성한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 리뷰 대상 상품 식별자. product 도메인 논리 참조. */
    @Column(name = "product_id")
    private UUID productId;

    /** 별점(1~5). */
    @Column
    private int rating;

    /** 리뷰 본문(1~1000자). */
    @Column
    private String content;

    /** 관리자 제거 시각. 제거된 리뷰만 있다. */
    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;

    /** 관리자 제거 사유. 제거된 리뷰만 있다. */
    @Column(name = "removed_reason")
    @Nullable
    private String removedReason;

    protected Review() {}

    private Review(UUID id, UUID memberId, UUID productId, int rating, String content) {
        this.id = id;
        this.memberId = memberId;
        this.productId = productId;
        this.rating = rating;
        this.content = content;
    }

    /**
     * 리뷰를 생성한다.
     *
     * @throws InvalidReviewException 별점·본문이 허용 범위를 벗어나면
     */
    public static Review create(UUID memberId, UUID productId, int rating, String content) {
        validate(rating, content);
        return new Review(UuidV7Generator.generate(), memberId, productId, rating, content);
    }

    /**
     * 별점·본문을 고쳐 쓴다.
     *
     * @throws InvalidReviewException 별점·본문이 허용 범위를 벗어나면
     */
    public void revise(int rating, String content) {
        validate(rating, content);
        this.rating = rating;
        this.content = content;
    }

    /** 별점·본문 허용 범위를 검증한다. */
    private static void validate(int rating, String content) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new InvalidReviewException(ReviewErrorCode.INVALID_RATING);
        }
        if (content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidReviewException(ReviewErrorCode.INVALID_CONTENT);
        }
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public @Nullable String getRemovedReason() {
        return removedReason;
    }

    /** 관리자 제거로 소프트삭제하고 사유를 보존한다. */
    public void delete(String reason) {
        this.deletedAt = Instant.now();
        this.removedReason = reason;
    }
}

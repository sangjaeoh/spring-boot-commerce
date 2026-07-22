package com.commerce.review.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.review.exception.InvalidReviewException;
import com.commerce.review.exception.ReviewErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    @DisplayName("범위 안 별점·본문으로 리뷰가 생성된다")
    void createSetsFields() {
        Review review = Review.create(MEMBER_ID, PRODUCT_ID, 5, "만족스러운 상품이다.");

        assertThat(review.getId()).isNotNull();
        assertThat(review.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(review.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("만족스러운 상품이다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    @DisplayName("별점이 1~5를 벗어나면 생성이 거부된다")
    void createRejectsRatingOutOfRange(int rating) {
        assertThatThrownBy(() -> Review.create(MEMBER_ID, PRODUCT_ID, rating, "본문"))
                .isInstanceOf(InvalidReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.INVALID_RATING);
    }

    @Test
    @DisplayName("본문이 공백이거나 1000자를 넘으면 생성이 거부된다")
    void createRejectsBlankOrOverlongContent() {
        assertThatThrownBy(() -> Review.create(MEMBER_ID, PRODUCT_ID, 3, "   "))
                .isInstanceOf(InvalidReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.INVALID_CONTENT);
        assertThatThrownBy(() -> Review.create(MEMBER_ID, PRODUCT_ID, 3, "가".repeat(1001)))
                .isInstanceOf(InvalidReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.INVALID_CONTENT);
    }

    @Test
    @DisplayName("고쳐 쓰기는 별점·본문을 바꾸고 같은 검증을 적용한다")
    void reviseReplacesFieldsWithSameValidation() {
        Review review = Review.create(MEMBER_ID, PRODUCT_ID, 5, "처음 본문");

        review.revise(2, "고친 본문");
        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getContent()).isEqualTo("고친 본문");

        assertThatThrownBy(() -> review.revise(0, "본문"))
                .isInstanceOf(InvalidReviewException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.INVALID_RATING);
    }
}

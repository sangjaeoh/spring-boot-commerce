package com.commerce.domain.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.review.application.info.ReviewInfo;
import com.commerce.domain.review.application.provided.ReviewAppender;
import com.commerce.domain.review.application.provided.ReviewModifier;
import com.commerce.domain.review.application.provided.ReviewReader;
import com.commerce.domain.review.application.provided.ReviewRemover;
import com.commerce.domain.review.application.required.ReviewRepository;
import com.commerce.domain.review.domain.Review;
import com.commerce.domain.review.domain.exception.DuplicateReviewException;
import com.commerce.domain.review.domain.exception.ReviewErrorCode;
import com.commerce.domain.review.domain.exception.ReviewNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * review 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code ddl-auto=validate} 정합, 작성·수정·삭제, (회원, 상품) 유니크, 최신 리뷰 우선 페이지를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/review",
            "spring.flyway.schemas=review",
            "spring.flyway.default-schema=review"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DefaultReviewAppender.class, DefaultReviewModifier.class, DefaultReviewRemover.class, DefaultReviewReader.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReviewPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final ReviewAppender reviewAppender;
    private final ReviewModifier reviewModifier;
    private final ReviewRemover reviewRemover;
    private final ReviewReader reviewReader;
    private final ReviewRepository reviewRepository;
    private final TestEntityManager entityManager;

    ReviewPersistenceTest(
            ReviewAppender reviewAppender,
            ReviewModifier reviewModifier,
            ReviewRemover reviewRemover,
            ReviewReader reviewReader,
            ReviewRepository reviewRepository,
            TestEntityManager entityManager) {
        this.reviewAppender = reviewAppender;
        this.reviewModifier = reviewModifier;
        this.reviewRemover = reviewRemover;
        this.reviewReader = reviewReader;
        this.reviewRepository = reviewRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("작성한 리뷰가 상품별 페이지에 별점·본문·작성 시각과 함께 보인다")
    void writeShowsReviewInProductPage() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        UUID reviewId = reviewAppender.write(memberId, productId, 4, "괜찮은 상품이다.");

        Page<ReviewInfo> page = reviewReader.getProductPage(productId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(info -> {
            assertThat(info.id()).isEqualTo(reviewId);
            assertThat(info.rating()).isEqualTo(4);
            assertThat(info.content()).isEqualTo("괜찮은 상품이다.");
            assertThat(info.writtenAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("같은 (회원, 상품)의 재작성은 중복으로 거부된다")
    void writeRejectsDuplicate() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        reviewAppender.write(memberId, productId, 4, "첫 리뷰");

        assertThatThrownBy(() -> reviewAppender.write(memberId, productId, 5, "둘째 리뷰"))
                .isInstanceOf(DuplicateReviewException.class);
    }

    @Test
    @DisplayName("(회원, 상품) 중복 행 직접 저장은 유니크 위반으로 거부된다 — 동시 경합의 최후 방어선")
    void duplicateRowViolatesUniqueIndex() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        reviewRepository.saveAndFlush(Review.create(memberId, productId, 3, "본문"));

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(Review.create(memberId, productId, 3, "본문")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("본인 리뷰를 고쳐 쓰면 페이지에 반영되고, 소유하지 않은 리뷰는 부재로 거부된다")
    void reviseAppliesToOwnReviewOnly() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reviewId = reviewAppender.write(memberId, productId, 4, "처음 본문");

        reviewModifier.revise(reviewId, memberId, 1, "고친 본문");
        assertThat(reviewReader.getProductPage(productId, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(info -> {
                    assertThat(info.rating()).isEqualTo(1);
                    assertThat(info.content()).isEqualTo("고친 본문");
                });

        assertThatThrownBy(() -> reviewModifier.revise(reviewId, UUID.randomUUID(), 5, "타인 수정"))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    @DisplayName("본인 리뷰를 삭제하면 페이지에서 빠지고, 소유하지 않은 리뷰는 부재로 거부된다")
    void removeAppliesToOwnReviewOnly() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reviewId = reviewAppender.write(memberId, productId, 4, "본문");

        assertThatThrownBy(() -> reviewRemover.remove(reviewId, UUID.randomUUID()))
                .isInstanceOf(ReviewNotFoundException.class);

        reviewRemover.remove(reviewId, memberId);
        assertThat(reviewReader.getProductPage(productId, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("관리자 제거 후 리뷰가 상품별 페이지에서 빠진다")
    void removeByAdminExcludesReviewFromProductPage() {
        UUID productId = UUID.randomUUID();
        UUID reviewId = reviewAppender.write(UUID.randomUUID(), productId, 1, "부적절한 본문");

        reviewRemover.removeByAdmin(reviewId, "욕설 포함");

        assertThat(reviewReader.getProductPage(productId, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("관리자 제거는 행에 사유·삭제 시각을 보존한다")
    void removeByAdminPreservesReasonOnRow() {
        UUID reviewId = reviewAppender.write(UUID.randomUUID(), UUID.randomUUID(), 1, "부적절한 본문");

        reviewRemover.removeByAdmin(reviewId, "욕설 포함");

        entityManager.flush();
        entityManager.clear();
        Review removed = Objects.requireNonNull(entityManager.find(Review.class, reviewId));
        assertThat(removed.getDeletedAt()).isNotNull();
        assertThat(removed.getRemovedReason()).isEqualTo("욕설 포함");
    }

    @Test
    @DisplayName("없는 리뷰·이미 제거된 리뷰의 관리자 제거는 REVIEW_NOT_FOUND로 거부된다")
    void removeByAdminRejectsMissingOrRemovedReview() {
        assertThatThrownBy(() -> reviewRemover.removeByAdmin(UUID.randomUUID(), "사유"))
                .isInstanceOf(ReviewNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);

        UUID reviewId = reviewAppender.write(UUID.randomUUID(), UUID.randomUUID(), 1, "부적절한 본문");
        reviewRemover.removeByAdmin(reviewId, "욕설 포함");
        assertThatThrownBy(() -> reviewRemover.removeByAdmin(reviewId, "재제거"))
                .isInstanceOf(ReviewNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("상품별 페이지는 최신 리뷰 우선으로 정렬된다")
    void productPageOrdersLatestFirst() {
        UUID productId = UUID.randomUUID();
        UUID firstReviewId = reviewAppender.write(UUID.randomUUID(), productId, 3, "먼저 쓴 리뷰");
        UUID secondReviewId = reviewAppender.write(UUID.randomUUID(), productId, 5, "나중에 쓴 리뷰");

        assertThat(reviewReader.getProductPage(productId, PageRequest.of(0, 10)).getContent())
                .extracting(ReviewInfo::id)
                .containsExactly(secondReviewId, firstReviewId);
    }
}

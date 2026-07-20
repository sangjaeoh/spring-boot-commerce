package com.commerce.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCoupon;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.CouponExpiredException;
import com.commerce.coupon.exception.DuplicateIssuanceException;
import com.commerce.coupon.exception.IssuedCouponNotFoundException;
import com.commerce.coupon.info.DiscountPreviewInfo;
import com.commerce.coupon.info.IssuedCouponInfo;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.shared.entity.Money;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * coupon 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>판별 유니온 {@link Discount}·다중 컬럼 {@link ValidityPeriod}의 {@code @Embedded} 왕복,
 * {@code ddl-auto=validate} 정합, 회원당 발급 유니크를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/coupon",
            "spring.flyway.schemas=coupon",
            "spring.flyway.default-schema=coupon"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({CouponAppender.class, IssuedCouponAppender.class, IssuedCouponReader.class, IssuedCouponModifier.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CouponPersistenceTest {

    private static final Instant FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2030-01-01T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponReader issuedCouponReader;
    private final IssuedCouponModifier issuedCouponModifier;
    private final TestEntityManager em;

    CouponPersistenceTest(
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponReader issuedCouponReader,
            IssuedCouponModifier issuedCouponModifier,
            TestEntityManager em) {
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponReader = issuedCouponReader;
        this.issuedCouponModifier = issuedCouponModifier;
        this.em = em;
    }

    private UUID createRateCoupon() {
        return couponAppender.create(
                "정률 10% (상한 2000)",
                Discount.rate(10, Money.of(2000L)), Money.of(10000L), ValidityPeriod.of(FROM, UNTIL), 30, null);
    }

    private UUID createFixedCoupon() {
        return couponAppender.create(
                "정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, ValidityPeriod.of(FROM, UNTIL), 30, null);
    }

    private UUID issueRateCouponTo(UUID memberId) {
        return issueTo(createRateCoupon(), memberId);
    }

    private UUID issueFixedCouponTo(UUID memberId) {
        return issueTo(createFixedCoupon(), memberId);
    }

    private UUID issueTo(UUID couponId, UUID memberId) {
        em.flush();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        em.flush();
        em.clear();
        return issuedId;
    }

    @Test
    @DisplayName("발급 후 조회·할인 산출 왕복 — 판별 유니온 @Embedded·validate 스키마 정합")
    void issueThenReadAndCalculate() {
        UUID couponId = createRateCoupon();
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueTo(couponId, memberId);

        Money discount = issuedCouponReader
                .getDiscountPreview(issuedId, memberId, Money.of(50000L))
                .discountAmount();
        assertThat(discount).isEqualTo(Money.of(2000L));

        IssuedCouponInfo info = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(info.couponId()).isEqualTo(couponId);
        assertThat(info.status()).isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("정액 쿠폰도 왕복한다 — discount_amount 컬럼 매핑 검증")
    void fixedDiscountRoundTrip() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueFixedCouponTo(memberId);

        assertThat(issuedCouponReader
                        .getDiscountPreview(issuedId, memberId, Money.of(5000L))
                        .discountAmount())
                .isEqualTo(Money.of(1000L));
    }

    @Test
    @DisplayName("회원당 동일 쿠폰 중복 발급은 거부된다")
    void duplicateIssuanceRejected() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID memberId = UUID.randomUUID();
        issuedCouponAppender.issue(couponId, memberId);
        em.flush();

        assertThatThrownBy(() -> issuedCouponAppender.issue(couponId, memberId))
                .isInstanceOf(DuplicateIssuanceException.class);
    }

    @Test
    @DisplayName("발급 시 사용 기한이 고정 Clock 시각+사용 창으로 스냅샷된다")
    void issueSnapshotsExpiresAtFromClock() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        em.flush();
        em.clear();

        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).expiresAt())
                .isEqualTo(PersistenceTestConfig.FIXED_NOW.plus(30, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("고정 Clock 기준 사용 기한 정각 사용은 성공하고 직후는 거부된다")
    void useExpiryBoundaryAgainstFixedClock() {
        IssuedCoupon atBoundary =
                em.persist(IssuedCoupon.create(UUID.randomUUID(), UUID.randomUUID(), PersistenceTestConfig.FIXED_NOW));
        IssuedCoupon justExpired = em.persist(IssuedCoupon.create(
                UUID.randomUUID(), UUID.randomUUID(), PersistenceTestConfig.FIXED_NOW.minusMillis(1)));
        em.flush();

        issuedCouponModifier.use(atBoundary.getId(), atBoundary.getMemberId(), UUID.randomUUID());
        assertThat(atBoundary.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        assertThatThrownBy(() ->
                        issuedCouponModifier.use(justExpired.getId(), justExpired.getMemberId(), UUID.randomUUID()))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    @DisplayName("미리보기 — 정액 쿠폰은 적용 가능과 예상 할인액을 싣는다")
    void previewFixedDiscountApplicable() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueFixedCouponTo(memberId);

        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.of(15000L));

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.reason()).isNull();
        assertThat(preview.discountAmount()).isEqualTo(Money.of(1000L));
    }

    @Test
    @DisplayName("미리보기 — 정률 쿠폰은 퍼센트 할인에 상한을 적용한다")
    void previewRateDiscountHonorsCap() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueRateCouponTo(memberId);

        assertThat(issuedCouponReader
                        .getDiscountPreview(issuedId, memberId, Money.of(15000L))
                        .discountAmount())
                .isEqualTo(Money.of(1500L));
        assertThat(issuedCouponReader
                        .getDiscountPreview(issuedId, memberId, Money.of(50000L))
                        .discountAmount())
                .isEqualTo(Money.of(2000L));
    }

    @Test
    @DisplayName("미리보기 — 최소주문금액 미달은 사유와 0원으로 표현된다")
    void previewBelowMinOrderNotApplicable() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueRateCouponTo(memberId);

        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.of(9999L));

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.reason()).isEqualTo(DiscountPreviewInfo.Reason.MIN_ORDER_AMOUNT_NOT_MET);
        assertThat(preview.discountAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("미리보기 — 고정 Clock 기준 기한 경과는 EXPIRED, 기한 정각은 적용 가능이다")
    void previewExpiryBoundaryAgainstFixedClock() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID expiredOwnerId = UUID.randomUUID();
        UUID boundaryOwnerId = UUID.randomUUID();
        IssuedCoupon justExpired = em.persist(
                IssuedCoupon.create(couponId, expiredOwnerId, PersistenceTestConfig.FIXED_NOW.minusMillis(1)));
        IssuedCoupon atBoundary =
                em.persist(IssuedCoupon.create(couponId, boundaryOwnerId, PersistenceTestConfig.FIXED_NOW));
        em.flush();

        DiscountPreviewInfo expired =
                issuedCouponReader.getDiscountPreview(justExpired.getId(), expiredOwnerId, Money.of(50000L));
        assertThat(expired.applicable()).isFalse();
        assertThat(expired.reason()).isEqualTo(DiscountPreviewInfo.Reason.EXPIRED);
        assertThat(expired.discountAmount()).isEqualTo(Money.ZERO);

        assertThat(issuedCouponReader
                        .getDiscountPreview(atBoundary.getId(), boundaryOwnerId, Money.of(50000L))
                        .applicable())
                .isTrue();
    }

    @Test
    @DisplayName("미리보기 — 사용된 발급분은 ALREADY_USED다")
    void previewUsedNotApplicable() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueRateCouponTo(memberId);
        issuedCouponModifier.use(issuedId, memberId, UUID.randomUUID());
        em.flush();
        em.clear();

        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.of(50000L));

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.reason()).isEqualTo(DiscountPreviewInfo.Reason.ALREADY_USED);
        assertThat(preview.discountAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("미리보기 — 무효화된 발급분은 REVOKED다")
    void previewRevokedNotApplicable() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueRateCouponTo(memberId);
        issuedCouponModifier.revoke(issuedId, "오발급 회수");
        em.flush();
        em.clear();

        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.of(50000L));

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.reason()).isEqualTo(DiscountPreviewInfo.Reason.REVOKED);
    }

    @Test
    @DisplayName("미리보기 — 최소주문금액은 넘지만 산출 할인이 0이면 ZERO_DISCOUNT다")
    void previewZeroDiscountNotApplicable() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueFixedCouponTo(memberId);

        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.ZERO);

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.reason()).isEqualTo(DiscountPreviewInfo.Reason.ZERO_DISCOUNT);
        assertThat(preview.discountAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("미리보기는 발급분 상태를 바꾸지 않는다")
    void previewDoesNotMutateIssuedCoupon() {
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issueRateCouponTo(memberId);

        issuedCouponReader.getDiscountPreview(issuedId, memberId, Money.of(50000L));
        em.flush();
        em.clear();

        IssuedCouponInfo info = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(info.status()).isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(info.usedAt()).isNull();
        assertThat(info.orderId()).isNull();
    }

    @Test
    @DisplayName("사용이 반영된다")
    void usePersists() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        em.flush();

        issuedCouponModifier.use(issuedId, memberId, UUID.randomUUID());
        em.flush();
        em.clear();

        IssuedCouponInfo info = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(info.status()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(info.orderId()).isNotNull();
    }

    @Test
    @DisplayName("타인 발급분 사용은 미존재로 거부되고 발급분은 ISSUED로 남는다")
    void useRejectsOtherMemberAsNotFound() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID ownerId = UUID.randomUUID();
        UUID issuedId = issuedCouponAppender.issue(couponId, ownerId);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> issuedCouponModifier.use(issuedId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IssuedCouponNotFoundException.class);

        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, ownerId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }
}

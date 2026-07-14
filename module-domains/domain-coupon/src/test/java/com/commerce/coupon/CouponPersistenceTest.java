package com.commerce.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.DuplicateIssuanceException;
import com.commerce.coupon.info.IssuedCouponInfo;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import java.time.Instant;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * coupon 도메인의 영속 이음새를 실 PostgreSQL로 검증한다.
 *
 * <p>판별 유니온 {@code Discount}·다중 컬럼 {@code ValidityPeriod}의 @Embedded 왕복,
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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

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
                Discount.rate(10, Money.of(2000L)), Money.of(10000L), ValidityPeriod.of(FROM, UNTIL), 30);
    }

    @Test
    @DisplayName("발급 후 조회·할인 산출 왕복 — 판별 유니온 @Embedded·validate 스키마 정합")
    void issueThenReadAndCalculate() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        em.flush();
        em.clear();

        Money discount = issuedCouponReader.calculateDiscount(issuedId, Money.of(50000L));
        assertThat(discount).isEqualTo(Money.of(2000L));

        IssuedCouponInfo info = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(info.couponId()).isEqualTo(couponId);
        assertThat(info.status()).isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("정액 쿠폰도 왕복한다 — discount_amount 컬럼 매핑 검증")
    void fixedDiscountRoundTrip() {
        UUID couponId = couponAppender.create(
                "정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, ValidityPeriod.of(FROM, UNTIL), 30);
        em.flush();
        UUID issuedId = issuedCouponAppender.issue(couponId, UUID.randomUUID());
        em.flush();
        em.clear();

        assertThat(issuedCouponReader.calculateDiscount(issuedId, Money.of(5000L)))
                .isEqualTo(Money.of(1000L));
    }

    @Test
    @DisplayName("주문 금액이 최소주문금액 미달이면 산출 할인은 0 — 체크아웃 게이트")
    void calculateDiscountBelowMinOrderIsZero() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID issuedId = issuedCouponAppender.issue(couponId, UUID.randomUUID());
        em.flush();
        em.clear();

        assertThat(issuedCouponReader.calculateDiscount(issuedId, Money.of(9999L)))
                .isEqualTo(Money.ZERO);
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
    @DisplayName("사용이 반영된다")
    void usePersists() {
        UUID couponId = createRateCoupon();
        em.flush();
        UUID memberId = UUID.randomUUID();
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        em.flush();

        issuedCouponModifier.use(issuedId, UUID.randomUUID());
        em.flush();
        em.clear();

        IssuedCouponInfo info = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(info.status()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(info.orderId()).isNotNull();
    }
}

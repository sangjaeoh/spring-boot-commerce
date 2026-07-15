package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.exception.DuplicateIssuanceException;
import com.commerce.coupon.exception.IssuedCouponNotFoundException;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.CouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberRemover;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CouponIssuanceFacadeTest extends FacadeIntegrationTest {

    private final CouponIssuanceFacade couponIssuanceFacade;
    private final CouponAppender couponAppender;
    private final CouponModifier couponModifier;
    private final IssuedCouponReader issuedCouponReader;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final MemberRemover memberRemover;

    CouponIssuanceFacadeTest(
            CouponIssuanceFacade couponIssuanceFacade,
            CouponAppender couponAppender,
            CouponModifier couponModifier,
            IssuedCouponReader issuedCouponReader,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            MemberRemover memberRemover) {
        this.couponIssuanceFacade = couponIssuanceFacade;
        this.couponAppender = couponAppender;
        this.couponModifier = couponModifier;
        this.issuedCouponReader = issuedCouponReader;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.memberRemover = memberRemover;
    }

    @Test
    @DisplayName("활성 회원 발급이 성공하고 발급분이 ISSUED로 조회된다")
    void issueSucceedsForActiveMember() {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();

        UUID issuedId = couponIssuanceFacade.issue(couponId, memberId);

        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("정지 회원 발급은 자격 없음으로 거부된다")
    void issueRejectsSuspendedMember() {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        assertThatThrownBy(() -> couponIssuanceFacade.issue(couponId, memberId))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.MEMBER_NOT_ELIGIBLE));
    }

    @Test
    @DisplayName("탈퇴 회원 발급은 회원 미존재로 거부된다")
    void issueRejectsWithdrawnMember() {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        memberRemover.delete(memberId, WithdrawalReason.NO_LONGER_USED);

        assertThatThrownBy(() -> couponIssuanceFacade.issue(couponId, memberId))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("같은 회원·쿠폰 재발급은 중복으로 거부된다")
    void issueRejectsDuplicate() {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        couponIssuanceFacade.issue(couponId, memberId);

        assertThatThrownBy(() -> couponIssuanceFacade.issue(couponId, memberId))
                .isInstanceOf(DuplicateIssuanceException.class);
    }

    @Test
    @DisplayName("발급 가능 기간 밖 쿠폰 발급은 거부된다")
    void issueRejectsOutsideIssuePeriod() {
        UUID memberId = registerMember();
        ValidityPeriod pastValidity =
                ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2021-01-01T00:00:00Z"));
        UUID couponId = couponAppender.create("과거 쿠폰", Discount.fixed(Money.of(1000L)), Money.ZERO, pastValidity, 30);

        assertThatThrownBy(() -> couponIssuanceFacade.issue(couponId, memberId))
                .isInstanceOfSatisfying(
                        CouponStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_OUTSIDE_ISSUE_PERIOD));
    }

    @Test
    @DisplayName("발급 중지된 쿠폰 발급은 거부된다")
    void issueRejectsDisabledCoupon() {
        UUID memberId = registerMember();
        UUID couponId = createCoupon();
        couponModifier.disable(couponId);

        assertThatThrownBy(() -> couponIssuanceFacade.issue(couponId, memberId))
                .isInstanceOfSatisfying(
                        CouponStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_DISABLED));
    }

    @Test
    @DisplayName("타인 발급분 조회는 미존재로 거부된다")
    void getIssuedCouponRejectsNonOwner() {
        UUID memberA = registerMember();
        UUID memberB = registerMember();
        UUID couponId = createCoupon();
        UUID issuedId = couponIssuanceFacade.issue(couponId, memberA);

        assertThatThrownBy(() -> issuedCouponReader.getIssuedCoupon(issuedId, memberB))
                .isInstanceOf(IssuedCouponNotFoundException.class);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID createCoupon() {
        return couponAppender.create("쿠폰", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30);
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급을 조율하며 회원 주문 자격 게이트를 적용한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 서비스를 조립한다(발급 도메인이 자기 트랜잭션 소유).
 */
@Component
public class CouponIssuanceFacade {

    private final MemberReader memberReader;
    private final IssuedCouponAppender issuedCouponAppender;

    public CouponIssuanceFacade(MemberReader memberReader, IssuedCouponAppender issuedCouponAppender) {
        this.memberReader = memberReader;
        this.issuedCouponAppender = issuedCouponAppender;
    }

    /**
     * 회원에게 쿠폰을 발급하고 새 발급분 ID를 반환한다.
     *
     * @throws ApiException 회원 자격이 활성이 아니면
     */
    public UUID issue(UUID couponId, UUID memberId) {
        requireEligibleMember(memberId);
        return issuedCouponAppender.issue(couponId, memberId);
    }

    /** 회원이 활성 자격인지 확인한다. */
    private void requireEligibleMember(UUID memberId) {
        MemberInfo member = memberReader.getMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_NOT_ELIGIBLE);
        }
    }
}

package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberRemover;
import com.commerce.order.service.OrderReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 회원 탈퇴를 미배송 결제 주문 가드와 함께 조율한다. */
@Component
public class MemberWithdrawalFacade {

    private final OrderReader orderReader;
    private final MemberRemover memberRemover;

    public MemberWithdrawalFacade(OrderReader orderReader, MemberRemover memberRemover) {
        this.orderReader = orderReader;
        this.memberRemover = memberRemover;
    }

    /**
     * 회원을 탈퇴 처리한다.
     *
     * @throws ApiException 미배송 결제 주문이 있어 탈퇴가 막히면
     */
    public void withdraw(UUID memberId, WithdrawalReason reason) {
        // 1. 미배송 결제 주문 가드
        if (orderReader.hasUndeliveredPaidOrder(memberId)) {
            throw new ApiException(ApiErrorCode.WITHDRAWAL_BLOCKED);
        }
        // 2. 회원 탈퇴
        memberRemover.delete(memberId, reason);
    }
}

package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberRemover;
import com.commerce.order.service.OrderReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴를 미배송 결제 주문 가드와 함께 조율한다.
 *
 * <p>member는 order에 컴파일 의존하지 않으므로(빌드 강제) 이 크로스 도메인 규칙은 파사드가 소유한다.
 * 미배송 결제완료 주문이 있으면 탈퇴를 거부하고, 없을 때만 논리삭제한다.
 */
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
        if (orderReader.hasUndeliveredPaidOrder(memberId)) {
            throw new ApiException(ApiErrorCode.WITHDRAWAL_BLOCKED);
        }
        memberRemover.delete(memberId, reason);
    }
}

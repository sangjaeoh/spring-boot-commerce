package com.commerce.api.presentation.v1;

import com.commerce.api.facade.MemberWithdrawalFacade;
import com.commerce.member.entity.WithdrawalReason;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴 엔드포인트다.
 *
 * <p>탈퇴 파사드에 얇게 위임한다. 미배송 결제 주문이 있으면 파사드가 탈퇴를 거부하고, 전역 핸들러가
 * 이를 problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberWithdrawalFacade memberWithdrawalFacade;

    public MemberController(MemberWithdrawalFacade memberWithdrawalFacade) {
        this.memberWithdrawalFacade = memberWithdrawalFacade;
    }

    /** 회원을 탈퇴(논리삭제) 처리한다. */
    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID memberId, @RequestParam WithdrawalReason reason) {
        memberWithdrawalFacade.withdraw(memberId, reason);
    }
}

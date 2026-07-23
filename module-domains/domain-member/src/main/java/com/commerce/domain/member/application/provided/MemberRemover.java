package com.commerce.domain.member.application.provided;

import com.commerce.domain.member.domain.WithdrawalReason;
import com.commerce.domain.member.domain.exception.MemberNotFoundException;
import java.util.UUID;

/** 회원 탈퇴(논리삭제)를 담당하는 서비스다. */
public interface MemberRemover {

    /**
     * 회원을 탈퇴 사유와 함께 논리삭제한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    void delete(UUID memberId, WithdrawalReason reason);
}

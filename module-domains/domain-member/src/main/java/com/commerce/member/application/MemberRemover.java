package com.commerce.member.application;

import com.commerce.member.application.required.MemberRepository;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberErrorCode;
import com.commerce.member.domain.MemberNotFoundException;
import com.commerce.member.domain.WithdrawalReason;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴(논리삭제)를 담당하는 서비스다. */
@Service
public class MemberRemover {

    private final MemberRepository memberRepository;
    private final Clock clock;

    public MemberRemover(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /**
     * 회원을 탈퇴 사유와 함께 논리삭제한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    @Transactional
    public void delete(UUID memberId, WithdrawalReason reason) {
        Member member = memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        member.delete(reason, clock.instant());
    }
}

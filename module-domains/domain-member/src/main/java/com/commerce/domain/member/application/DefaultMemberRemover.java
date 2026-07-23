package com.commerce.domain.member.application;

import com.commerce.domain.member.application.provided.MemberRemover;
import com.commerce.domain.member.application.required.MemberRepository;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.WithdrawalReason;
import com.commerce.domain.member.domain.exception.MemberErrorCode;
import com.commerce.domain.member.domain.exception.MemberNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberRemover}의 기본 구현이다. */
@Service
class DefaultMemberRemover implements MemberRemover {

    private final MemberRepository memberRepository;
    private final Clock clock;

    DefaultMemberRemover(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void delete(UUID memberId, WithdrawalReason reason) {
        Member member = memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        member.delete(reason, clock.instant());
    }
}

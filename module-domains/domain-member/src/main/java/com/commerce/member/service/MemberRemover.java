package com.commerce.member.service;

import com.commerce.member.entity.Member;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.repository.MemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴(논리삭제)를 담당한다. */
@Service
public class MemberRemover {

    private final MemberRepository memberRepository;

    public MemberRemover(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
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
        member.delete(reason);
    }
}

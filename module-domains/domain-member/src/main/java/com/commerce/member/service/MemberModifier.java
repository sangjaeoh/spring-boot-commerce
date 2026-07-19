package com.commerce.member.service;

import com.commerce.member.entity.Member;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.exception.MemberStatusException;
import com.commerce.member.repository.MemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 상태 전이·표시 이름 변경을 담당한다. */
@Service
public class MemberModifier {

    private final MemberRepository memberRepository;

    public MemberModifier(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 회원을 정지한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws MemberStatusException 활성 상태가 아니면
     */
    @Transactional
    public void suspend(UUID memberId, SuspensionReason reason) {
        find(memberId).suspend(reason);
    }

    /**
     * 회원 정지를 해제한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws MemberStatusException 정지 상태가 아니면
     */
    @Transactional
    public void reinstate(UUID memberId) {
        find(memberId).reinstate();
    }

    /**
     * 회원 표시 이름을 바꾼다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    @Transactional
    public void rename(UUID memberId, String newName) {
        find(memberId).rename(newName);
    }

    /** 활성 회원을 찾고 없으면 거부한다. */
    private Member find(UUID memberId) {
        return memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}

package com.commerce.domain.member.application;

import com.commerce.domain.member.application.info.MemberInfo;
import com.commerce.domain.member.application.provided.MemberReader;
import com.commerce.domain.member.application.required.MemberRepository;
import com.commerce.domain.member.domain.Email;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.exception.MemberErrorCode;
import com.commerce.domain.member.domain.exception.MemberNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberReader}의 기본 구현이다. */
@Service
class DefaultMemberReader implements MemberReader {

    private final MemberRepository memberRepository;

    DefaultMemberReader(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public MemberInfo getMember(UUID memberId) {
        Member member = memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberInfo.from(member);
    }

    @Transactional(readOnly = true)
    @Override
    public MemberInfo getMemberByEmail(String email) {
        Member member = memberRepository
                .findByEmailAndDeletedAtIsNull(Email.of(email))
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberInfo.from(member);
    }
}

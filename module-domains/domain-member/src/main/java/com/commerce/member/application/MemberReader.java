package com.commerce.member.application;

import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.required.MemberRepository;
import com.commerce.member.domain.Email;
import com.commerce.member.domain.InvalidEmailException;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberErrorCode;
import com.commerce.member.domain.MemberNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 조회를 담당하는 서비스다. */
@Service
public class MemberReader {

    private final MemberRepository memberRepository;

    public MemberReader(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 활성 회원을 조회한다. 정지 회원·정지 사유를 포함한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    @Transactional(readOnly = true)
    public MemberInfo getMember(UUID memberId) {
        Member member = memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberInfo.from(member);
    }

    /**
     * 이메일 정확 일치로 활성 회원을 조회한다. 정지 회원·정지 사유를 포함한다.
     *
     * @throws MemberNotFoundException 해당 이메일의 활성 회원이 없으면
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    @Transactional(readOnly = true)
    public MemberInfo getMemberByEmail(String email) {
        Member member = memberRepository
                .findByEmailAndDeletedAtIsNull(Email.of(email))
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberInfo.from(member);
    }
}

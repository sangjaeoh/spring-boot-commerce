package com.commerce.member.service;

import com.commerce.member.entity.Email;
import com.commerce.member.entity.Member;
import com.commerce.member.exception.DuplicateEmailException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.repository.MemberRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 가입을 담당한다. */
@Service
public class MemberAppender {

    private final MemberRepository memberRepository;

    public MemberAppender(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 회원을 가입시키고 새 회원 ID를 반환한다.
     *
     * @throws DuplicateEmailException 활성 회원 사이에서 이메일이 이미 쓰일 때
     */
    @Transactional
    public UUID register(String email, String name) {
        Email emailValue = Email.of(email);
        if (memberRepository.existsByEmailAndDeletedAtIsNull(emailValue)) {
            throw new DuplicateEmailException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        try {
            return memberRepository
                    .saveAndFlush(Member.create(emailValue, name))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 가입 경합 방어
            throw new DuplicateEmailException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }
}

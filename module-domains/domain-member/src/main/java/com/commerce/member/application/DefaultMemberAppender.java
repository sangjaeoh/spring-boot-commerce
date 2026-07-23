package com.commerce.member.application;

import com.commerce.member.application.provided.MemberAppender;
import com.commerce.member.application.required.MemberRepository;
import com.commerce.member.domain.Email;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberRole;
import com.commerce.member.domain.exception.DuplicateEmailException;
import com.commerce.member.domain.exception.InvalidPasswordException;
import com.commerce.member.domain.exception.MemberErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberAppender}의 기본 구현이다. */
@Service
class DefaultMemberAppender implements MemberAppender {

    // bcrypt 입력 한계. 자격증명 검증(MemberCredentialValidator)의 가드도 이 값을 쓴다.
    static final int MAX_PASSWORD_BYTES = 72;

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    DefaultMemberAppender(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    @Override
    public UUID register(String email, String name, String rawPassword) {
        return append(email, name, rawPassword, MemberRole.BUYER);
    }

    @Transactional
    @Override
    public UUID registerAdmin(String email, String name, String rawPassword) {
        return append(email, name, rawPassword, MemberRole.ADMIN);
    }

    /** 이메일·패스워드를 검증하고 주어진 역할로 회원을 저장한다. */
    private UUID append(String email, String name, String rawPassword, MemberRole role) {
        Email emailValue = Email.of(email);
        validatePassword(rawPassword);
        if (memberRepository.existsByEmailAndDeletedAtIsNull(emailValue)) {
            throw new DuplicateEmailException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        try {
            return memberRepository
                    .saveAndFlush(Member.create(emailValue, name, passwordEncoder.encode(rawPassword), role))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            // 선검사와 저장 사이 동시 가입 경합 방어
            throw new DuplicateEmailException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }

    /** 패스워드가 길이 정책을 벗어나면 거부한다. 패스워드 정책의 유일한 소유처라 패스워드 변경 경로도 이를 쓴다. */
    static void validatePassword(String rawPassword) {
        if (rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException(MemberErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }
}

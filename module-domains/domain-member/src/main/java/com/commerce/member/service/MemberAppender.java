package com.commerce.member.service;

import com.commerce.member.entity.Email;
import com.commerce.member.entity.Member;
import com.commerce.member.entity.MemberRole;
import com.commerce.member.exception.DuplicateEmailException;
import com.commerce.member.exception.InvalidEmailException;
import com.commerce.member.exception.InvalidPasswordException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 가입을 담당하는 서비스다. */
@Service
public class MemberAppender {

    // bcrypt 입력 한계. 자격증명 검증(MemberCredentialValidator)의 가드도 이 값을 쓴다.
    static final int MAX_PASSWORD_BYTES = 72;

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberAppender(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 구매자 회원을 가입시키고 새 회원 ID를 반환한다. 패스워드는 bcrypt 해시로만 저장한다(평문 비보관).
     *
     * @throws DuplicateEmailException 활성 회원 사이에서 이메일이 이미 쓰일 때
     * @throws InvalidPasswordException 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    @Transactional
    public UUID register(String email, String name, String rawPassword) {
        return append(email, name, rawPassword, MemberRole.BUYER);
    }

    /**
     * 관리자 회원을 가입시키고 새 회원 ID를 반환한다. 공개 가입 경로가 아니라 기동 시딩 전용이다.
     *
     * @throws DuplicateEmailException 활성 회원 사이에서 이메일이 이미 쓰일 때
     * @throws InvalidPasswordException 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    @Transactional
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

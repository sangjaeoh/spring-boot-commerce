package com.commerce.member.service;

import com.commerce.member.entity.Email;
import com.commerce.member.entity.Member;
import com.commerce.member.exception.InvalidCredentialsException;
import com.commerce.member.exception.InvalidEmailException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 자격증명 검증을 담당한다. */
@Service
public class MemberCredentialValidator {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberCredentialValidator(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 이메일+패스워드 자격증명을 검증하고 검증된 회원을 반환한다. 정지 회원은 통과한다 — 차단은 담기·체크아웃
     * 자격 게이트가 담당한다.
     *
     * @throws InvalidCredentialsException 미존재·탈퇴·패스워드 불일치 — 원인을 구분하지 않는다(계정 존재 노출 방지)
     */
    @Transactional(readOnly = true)
    public MemberInfo authenticate(String email, String rawPassword) {
        Member member = memberRepository
                .findByEmailAndDeletedAtIsNull(parseEmail(email))
                .orElseThrow(() -> new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS));
        // 가입이 72바이트 이하를 강제하므로 초과 입력은 어떤 저장 해시와도 일치할 수 없다(encoder 예외 선차단).
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MemberAppender.MAX_PASSWORD_BYTES
                || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS);
        }
        return MemberInfo.from(member);
    }

    // 형식이 어긋난 이메일은 어떤 계정에도 속하지 않으므로 형식 오류(400)가 아니라 동일 거부(401)로 응답한다.
    private static Email parseEmail(String email) {
        try {
            return Email.of(email);
        } catch (InvalidEmailException e) {
            throw new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS);
        }
    }
}

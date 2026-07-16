package com.commerce.member.service;

import com.commerce.member.entity.Email;
import com.commerce.member.entity.Member;
import com.commerce.member.exception.InvalidCredentialsException;
import com.commerce.member.exception.InvalidEmailException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 자격증명 검증을 담당한다. */
@Service
public class MemberCredentialValidator {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 미존재·탈퇴 경로에서도 bcrypt를 한 번 태워 실 계정과 연산량을 동일화하는 고정 더미 해시.
    // 실 해시와 같은 인코더(같은 코스트)로 만들어 조기 반환을 막는다. 검증 결과는 인증에 쓰지 않는다.
    private final String dummyPasswordHash;

    @Autowired
    public MemberCredentialValidator(MemberRepository memberRepository) {
        this(memberRepository, new BCryptPasswordEncoder());
    }

    MemberCredentialValidator(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-placeholder");
    }

    /**
     * 이메일+패스워드 자격증명을 검증하고 검증된 회원을 반환한다. 정지 회원은 통과한다 — 차단은 담기·체크아웃
     * 자격 게이트가 담당한다.
     *
     * <p>미존재·탈퇴 이메일에서도 고정 더미 해시로 bcrypt를 한 번 태워, 응답 시간 차로 계정 존재를 유추하지
     * 못하게 한다.
     *
     * @throws InvalidCredentialsException 미존재·탈퇴·패스워드 불일치 — 원인을 구분하지 않는다(계정 존재 노출 방지)
     */
    @Transactional(readOnly = true)
    public MemberInfo authenticate(String email, String rawPassword) {
        Optional<Member> found = memberRepository.findByEmailAndDeletedAtIsNull(parseEmail(email));
        // 미존재·탈퇴는 더미 해시로 대체해 존재 계정과 같은 bcrypt 연산을 태운다.
        String passwordHash = found.map(Member::getPasswordHash).orElse(dummyPasswordHash);
        // 가입이 72바이트 이하를 강제하므로 초과 입력은 어떤 저장 해시와도 일치할 수 없다(encoder 예외 선차단).
        boolean matched = rawPassword.getBytes(StandardCharsets.UTF_8).length <= MemberAppender.MAX_PASSWORD_BYTES
                && passwordEncoder.matches(rawPassword, passwordHash);
        // 인증 성공은 회원 존재 + 일치를 동시에 요구한다. 더미 비교가 우연히 true여도 미존재는 항상 거부된다.
        if (found.isPresent() && matched) {
            return MemberInfo.from(found.get());
        }
        throw new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS);
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

package com.commerce.domain.member.application;

import com.commerce.domain.member.application.info.MemberInfo;
import com.commerce.domain.member.application.provided.MemberCredentialValidator;
import com.commerce.domain.member.application.required.MemberRepository;
import com.commerce.domain.member.domain.Email;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.exception.InvalidCredentialsException;
import com.commerce.domain.member.domain.exception.InvalidEmailException;
import com.commerce.domain.member.domain.exception.MemberErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberCredentialValidator}의 기본 구현이다. */
@Service
class DefaultMemberCredentialValidator implements MemberCredentialValidator {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 미존재·탈퇴 경로에서도 bcrypt를 한 번 태워 실 계정과 연산량을 동일화하는 고정 더미 해시.
    // 실 해시와 같은 인코더(같은 코스트)로 만들어 조기 반환을 막는다. 검증 결과는 인증에 쓰지 않는다.
    private final String dummyPasswordHash;

    @Autowired
    DefaultMemberCredentialValidator(MemberRepository memberRepository) {
        this(memberRepository, new BCryptPasswordEncoder());
    }

    DefaultMemberCredentialValidator(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-placeholder");
    }

    @Transactional(readOnly = true)
    @Override
    public MemberInfo authenticate(String email, String rawPassword) {
        Optional<Member> found = memberRepository.findByEmailAndDeletedAtIsNull(parseEmail(email));
        // 미존재·탈퇴는 더미 해시로 대체해 존재 계정과 같은 bcrypt 연산을 태운다.
        String passwordHash = found.map(Member::getPasswordHash).orElse(dummyPasswordHash);
        // 가입이 72바이트 이하를 강제하므로 초과 입력은 어떤 저장 해시와도 일치할 수 없다(encoder 예외 선차단).
        boolean matched =
                rawPassword.getBytes(StandardCharsets.UTF_8).length <= DefaultMemberAppender.MAX_PASSWORD_BYTES
                        && passwordEncoder.matches(rawPassword, passwordHash);
        // 인증 성공은 회원 존재 + 일치를 동시에 요구한다. 더미 비교가 우연히 true여도 미존재는 항상 거부된다.
        if (found.isPresent() && matched) {
            return MemberInfo.from(found.get());
        }
        throw new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS);
    }

    /** 이메일을 파싱하고 형식 오류를 자격증명 거부로 바꾼다. */
    private static Email parseEmail(String email) {
        // 형식이 어긋난 이메일은 어떤 계정에도 속하지 않으므로 형식 오류(400)가 아니라 동일 거부(401)로 응답한다.
        try {
            return Email.of(email);
        } catch (InvalidEmailException e) {
            throw new InvalidCredentialsException(MemberErrorCode.INVALID_CREDENTIALS);
        }
    }
}

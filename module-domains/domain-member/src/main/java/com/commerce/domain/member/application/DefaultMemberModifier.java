package com.commerce.domain.member.application;

import com.commerce.domain.member.application.provided.MemberModifier;
import com.commerce.domain.member.application.required.MemberRepository;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.SuspensionReason;
import com.commerce.domain.member.domain.exception.MemberErrorCode;
import com.commerce.domain.member.domain.exception.MemberNotFoundException;
import com.commerce.domain.member.domain.exception.PasswordMismatchException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberModifier}의 기본 구현이다. */
@Service
class DefaultMemberModifier implements MemberModifier {

    private final MemberRepository memberRepository;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    DefaultMemberModifier(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void suspend(UUID memberId, SuspensionReason reason) {
        find(memberId).suspend(reason);
    }

    @Transactional
    @Override
    public void reinstate(UUID memberId) {
        find(memberId).reinstate();
    }

    @Transactional
    @Override
    public void rename(UUID memberId, String newName) {
        find(memberId).rename(newName);
    }

    @Transactional
    @Override
    public void replacePassword(UUID memberId, String currentRawPassword, String newRawPassword) {
        Member member = find(memberId);
        // 가입이 72바이트 이하를 강제하므로 초과 입력은 어떤 저장 해시와도 일치할 수 없다(encoder 예외 선차단).
        boolean matched =
                currentRawPassword.getBytes(StandardCharsets.UTF_8).length <= DefaultMemberAppender.MAX_PASSWORD_BYTES
                        && passwordEncoder.matches(currentRawPassword, member.getPasswordHash());
        if (!matched) {
            throw new PasswordMismatchException(MemberErrorCode.PASSWORD_MISMATCH);
        }
        DefaultMemberAppender.validatePassword(newRawPassword);
        member.replacePassword(passwordEncoder.encode(newRawPassword));
    }

    @Transactional
    @Override
    public void resetPassword(UUID memberId, String newRawPassword) {
        Member member = find(memberId);
        DefaultMemberAppender.validatePassword(newRawPassword);
        member.replacePassword(passwordEncoder.encode(newRawPassword));
    }

    @Transactional
    @Override
    public void verifyEmail(UUID memberId) {
        find(memberId).verifyEmail(clock.instant());
    }

    /** 활성 회원을 찾고 없으면 거부한다. */
    private Member find(UUID memberId) {
        return memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}

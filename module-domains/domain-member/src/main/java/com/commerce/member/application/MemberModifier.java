package com.commerce.member.application;

import com.commerce.member.application.required.MemberRepository;
import com.commerce.member.domain.InvalidPasswordException;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberErrorCode;
import com.commerce.member.domain.MemberNotFoundException;
import com.commerce.member.domain.MemberStatusException;
import com.commerce.member.domain.PasswordMismatchException;
import com.commerce.member.domain.SuspensionReason;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 상태 전이·표시 이름 변경·패스워드 교체를 담당하는 서비스다. */
@Service
public class MemberModifier {

    private final MemberRepository memberRepository;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberModifier(MemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
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

    /**
     * 현재 패스워드를 대조하고 새 패스워드로 교체한다. 새 패스워드는 bcrypt 해시로만 저장한다(평문 비보관).
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws PasswordMismatchException 현재 패스워드가 일치하지 않으면
     * @throws InvalidPasswordException 새 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     */
    @Transactional
    public void replacePassword(UUID memberId, String currentRawPassword, String newRawPassword) {
        Member member = find(memberId);
        // 가입이 72바이트 이하를 강제하므로 초과 입력은 어떤 저장 해시와도 일치할 수 없다(encoder 예외 선차단).
        boolean matched =
                currentRawPassword.getBytes(StandardCharsets.UTF_8).length <= MemberAppender.MAX_PASSWORD_BYTES
                        && passwordEncoder.matches(currentRawPassword, member.getPasswordHash());
        if (!matched) {
            throw new PasswordMismatchException(MemberErrorCode.PASSWORD_MISMATCH);
        }
        MemberAppender.validatePassword(newRawPassword);
        member.replacePassword(passwordEncoder.encode(newRawPassword));
    }

    /**
     * 현재 패스워드 대조 없이 새 패스워드로 재설정한다. 재설정 토큰 검증을 마친 호출자만 부른다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws InvalidPasswordException 새 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     */
    @Transactional
    public void resetPassword(UUID memberId, String newRawPassword) {
        Member member = find(memberId);
        MemberAppender.validatePassword(newRawPassword);
        member.replacePassword(passwordEncoder.encode(newRawPassword));
    }

    /**
     * 이메일 소유 인증을 기록한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    @Transactional
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

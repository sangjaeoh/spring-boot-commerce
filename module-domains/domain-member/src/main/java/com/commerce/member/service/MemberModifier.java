package com.commerce.member.service;

import com.commerce.member.entity.Member;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.exception.InvalidPasswordException;
import com.commerce.member.exception.MemberErrorCode;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.exception.MemberStatusException;
import com.commerce.member.exception.PasswordMismatchException;
import com.commerce.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 상태 전이·표시 이름 변경·패스워드 교체를 담당하는 서비스다. */
@Service
public class MemberModifier {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberModifier(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
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

    /** 활성 회원을 찾고 없으면 거부한다. */
    private Member find(UUID memberId) {
        return memberRepository
                .findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberNotFoundException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}

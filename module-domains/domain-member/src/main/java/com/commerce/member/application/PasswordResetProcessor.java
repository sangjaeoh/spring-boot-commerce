package com.commerce.member.application;

import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.required.MailGateway;
import com.commerce.member.application.required.OneTimeTokenStore;
import com.commerce.member.domain.MemberNotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 비밀번호 재설정(메일 요청·토큰 검증·교체)을 조율하는 서비스다. */
@Service
public class PasswordResetProcessor {

    /** 재설정 토큰의 1회용 저장 네임스페이스. */
    static final String PASSWORD_RESET_NAMESPACE = "password-reset";

    private final MemberReader memberReader;
    private final MemberModifier memberModifier;
    private final OneTimeTokenStore oneTimeTokenStore;
    private final MailGateway mailGateway;
    private final Duration passwordResetTokenTtl;

    public PasswordResetProcessor(
            MemberReader memberReader,
            MemberModifier memberModifier,
            OneTimeTokenStore oneTimeTokenStore,
            MailGateway mailGateway,
            // 미설정 앱(재설정 표면 없는 admin·batch)도 컨텍스트가 뜨도록 기본값을 둔다. 실서빙 값은 앱 설정이 소유한다.
            @Value("${auth.password-reset-token-ttl:30m}") Duration passwordResetTokenTtl) {
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
        this.oneTimeTokenStore = oneTimeTokenStore;
        this.mailGateway = mailGateway;
        this.passwordResetTokenTtl = passwordResetTokenTtl;
    }

    /** 가입 이메일이면 재설정 토큰을 보관하고 메일을 보낸다. 미가입 이메일은 조용히 무시한다(계정 존재 비노출). */
    public void requestReset(String email) {
        MemberInfo member;
        try {
            member = memberReader.getMemberByEmail(email);
        } catch (MemberNotFoundException e) {
            return;
        }
        String token = UUID.randomUUID().toString();
        oneTimeTokenStore.save(PASSWORD_RESET_NAMESPACE, token, member.id().toString(), passwordResetTokenTtl);
        mailGateway.sendPasswordResetMail(member.email(), token);
    }

    /**
     * 재설정 토큰을 소비해 새 비밀번호로 교체한다. 토큰은 1회용이다.
     *
     * @return 교체된 회원 ID. 무효 토큰(위조·만료·기사용)이면 빈 결과
     */
    public Optional<UUID> reset(String token, String newPassword) {
        Optional<String> subject = oneTimeTokenStore.consume(PASSWORD_RESET_NAMESPACE, token);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        UUID memberId = UUID.fromString(subject.get());
        memberModifier.resetPassword(memberId, newPassword);
        return Optional.of(memberId);
    }
}

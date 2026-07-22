package com.commerce.member.application;

import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.application.provided.MemberModifier;
import com.commerce.member.application.provided.MemberReader;
import com.commerce.member.application.provided.PasswordResetProcessor;
import com.commerce.member.application.required.MailGateway;
import com.commerce.member.application.required.OneTimeTokenStore;
import com.commerce.member.domain.MemberNotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** {@link PasswordResetProcessor}의 기본 구현이다. */
@Service
class DefaultPasswordResetProcessor implements PasswordResetProcessor {

    /** 재설정 토큰의 1회용 저장 네임스페이스. */
    static final String PASSWORD_RESET_NAMESPACE = "password-reset";

    private final MemberReader memberReader;
    private final MemberModifier memberModifier;
    private final OneTimeTokenStore oneTimeTokenStore;
    private final MailGateway mailGateway;
    private final Duration passwordResetTokenTtl;

    DefaultPasswordResetProcessor(
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

    @Override
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

    @Override
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

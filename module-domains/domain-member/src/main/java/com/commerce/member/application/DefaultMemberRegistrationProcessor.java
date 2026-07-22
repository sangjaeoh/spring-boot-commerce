package com.commerce.member.application;

import com.commerce.member.application.provided.MemberAppender;
import com.commerce.member.application.provided.MemberModifier;
import com.commerce.member.application.provided.MemberRegistrationProcessor;
import com.commerce.member.application.required.MailGateway;
import com.commerce.member.application.required.OneTimeTokenStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** {@link MemberRegistrationProcessor}의 기본 구현이다. */
@Service
class DefaultMemberRegistrationProcessor implements MemberRegistrationProcessor {

    /** 이메일 인증 토큰의 1회용 저장 네임스페이스. */
    static final String EMAIL_VERIFICATION_NAMESPACE = "email-verification";

    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final OneTimeTokenStore oneTimeTokenStore;
    private final MailGateway mailGateway;
    private final Duration emailVerificationTokenTtl;

    DefaultMemberRegistrationProcessor(
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            OneTimeTokenStore oneTimeTokenStore,
            MailGateway mailGateway,
            // 미설정 앱(가입 표면 없는 admin·batch)도 컨텍스트가 뜨도록 기본값을 둔다. 실서빙 값은 앱 설정이 소유한다.
            @Value("${auth.email-verification-token-ttl:24h}") Duration emailVerificationTokenTtl) {
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.oneTimeTokenStore = oneTimeTokenStore;
        this.mailGateway = mailGateway;
        this.emailVerificationTokenTtl = emailVerificationTokenTtl;
    }

    @Override
    public UUID register(String email, String name, String rawPassword) {
        UUID memberId = memberAppender.register(email, name, rawPassword);
        String token = UUID.randomUUID().toString();
        oneTimeTokenStore.save(EMAIL_VERIFICATION_NAMESPACE, token, memberId.toString(), emailVerificationTokenTtl);
        mailGateway.sendVerificationMail(email, token);
        return memberId;
    }

    @Override
    public Optional<UUID> verifyEmail(String token) {
        Optional<String> subject = oneTimeTokenStore.consume(EMAIL_VERIFICATION_NAMESPACE, token);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        UUID memberId = UUID.fromString(subject.get());
        memberModifier.verifyEmail(memberId);
        return Optional.of(memberId);
    }
}

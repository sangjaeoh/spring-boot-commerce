package com.commerce.member.application;

import com.commerce.member.application.required.MailGateway;
import com.commerce.member.application.required.OneTimeTokenStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 회원 가입 접수(인증 메일 발송)와 이메일 인증을 조율하는 서비스다. */
@Service
public class MemberRegistrationProcessor {

    /** 이메일 인증 토큰의 1회용 저장 네임스페이스. */
    static final String EMAIL_VERIFICATION_NAMESPACE = "email-verification";

    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final OneTimeTokenStore oneTimeTokenStore;
    private final MailGateway mailGateway;
    private final Duration emailVerificationTokenTtl;

    public MemberRegistrationProcessor(
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

    /**
     * 회원을 가입시키고 인증 토큰을 보관한 뒤 인증 메일을 보낸다.
     *
     * @return 가입된 회원 ID
     */
    public UUID register(String email, String name, String rawPassword) {
        UUID memberId = memberAppender.register(email, name, rawPassword);
        String token = UUID.randomUUID().toString();
        oneTimeTokenStore.save(EMAIL_VERIFICATION_NAMESPACE, token, memberId.toString(), emailVerificationTokenTtl);
        mailGateway.sendVerificationMail(email, token);
        return memberId;
    }

    /**
     * 인증 토큰을 소비해 이메일 소유 인증을 기록한다. 토큰은 1회용이다.
     *
     * @return 인증된 회원 ID. 무효 토큰(위조·만료·기사용)이면 빈 결과
     */
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

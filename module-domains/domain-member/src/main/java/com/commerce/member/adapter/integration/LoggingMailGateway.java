package com.commerce.member.adapter.integration;

import com.commerce.member.application.required.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 발송 대신 로그로 남기는 개발용 메일 게이트웨이다. */
@Component
final class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    @Override
    public void sendPasswordResetMail(String to, String token) {
        log.info("[메일] 비밀번호 재설정 안내 to={} token={}", to, token);
    }

    @Override
    public void sendVerificationMail(String to, String token) {
        log.info("[메일] 이메일 인증 안내 to={} token={}", to, token);
    }
}

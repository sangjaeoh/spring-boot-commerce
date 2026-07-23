package com.commerce.domain.wishlist.adapter.integration;

import com.commerce.domain.wishlist.application.required.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 발송을 로그로 대체하는 개발용 {@link MailGateway} 구현이다. member의 동명 구현과 빈 이름이 겹치지 않게 접두를 둔다. */
@Component
class LoggingRestockMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingRestockMailGateway.class);

    @Override
    public void sendRestockMail(String to, String productName) {
        log.info("재입고 알림 메일 발송: to={}, productName={}", to, productName);
    }
}

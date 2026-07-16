package com.commerce.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 규칙 지점(쿠폰 발급 기간·만료 판정)이 주입 시각으로 동작하도록 앱 전역 {@link Clock}을 공급한다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

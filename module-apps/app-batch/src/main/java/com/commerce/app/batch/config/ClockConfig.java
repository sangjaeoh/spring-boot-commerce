package com.commerce.app.batch.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 앱 전역 {@link Clock}을 배선하는 설정이다. */
@Configuration
public class ClockConfig {

    /** 시각을 다루는 코드가 주입받는 UTC 기준 시스템 시계를 공급한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

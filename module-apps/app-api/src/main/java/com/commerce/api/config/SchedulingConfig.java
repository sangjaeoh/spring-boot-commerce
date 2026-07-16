package com.commerce.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 잡(결제 리컨실·PENDING 주문 스윕) 실행을 활성화한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}

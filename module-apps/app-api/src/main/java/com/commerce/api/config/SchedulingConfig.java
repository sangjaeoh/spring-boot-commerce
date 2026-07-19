package com.commerce.api.config;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 잡(결제 리컨실·PENDING 주문 스윕) 실행을 활성화한다.
 *
 * <p>다중 인스턴스 배포에서 전 노드가 같은 스윕을 동시 실행하지 않도록 ShedLock 분산 락을 함께 켠다
 * (락 저장소는 Redis, 락 제공 빈은 infra-redis 소유 — 근거는 REQUIREMENTS.md 제약·전제).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {}

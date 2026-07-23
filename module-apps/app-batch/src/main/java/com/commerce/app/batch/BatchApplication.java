package com.commerce.app.batch;

import com.commerce.common.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 커머스 배치 실행 앱이다. 스케줄 스윕(결제 리컨실·미결 주문 스윕)과 PG 웹훅 확정 경로를 소유한다. */
@SpringBootApplication(scanBasePackages = "com.commerce")
@EntityScan("com.commerce")
@EnableJpaRepositories("com.commerce")
@Import(JpaAuditingConfig.class)
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}

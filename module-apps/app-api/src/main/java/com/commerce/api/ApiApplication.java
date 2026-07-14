package com.commerce.api;

import com.commerce.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 커머스 API 실행 앱이다.
 *
 * <p>도메인 서비스({@code @Service})·external 어댑터·in-process 발행 transport를 {@code com.commerce}
 * 전역 스캔으로 조립하고, 파사드가 크로스 도메인 흐름을 트랜잭션 없이 조율한다. 엔티티·리포지토리 스캔도
 * {@code com.commerce}로 넓혀 도메인 모듈의 매핑·저장소를 포함한다.
 */
@SpringBootApplication(scanBasePackages = "com.commerce")
@EntityScan("com.commerce")
@EnableJpaRepositories("com.commerce")
@Import(JpaAuditingConfig.class)
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}

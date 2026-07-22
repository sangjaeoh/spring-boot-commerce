package com.commerce.admin;

import com.commerce.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 커머스 어드민 실행 앱이다. 관리자 오퍼레이션 표면(`/api/v1/admin/**`)과 관리자 계정 시딩을 소유한다. */
@SpringBootApplication(scanBasePackages = "com.commerce")
@EntityScan("com.commerce")
@EnableJpaRepositories("com.commerce")
@Import(JpaAuditingConfig.class)
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

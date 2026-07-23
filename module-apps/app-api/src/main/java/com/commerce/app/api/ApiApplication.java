package com.commerce.app.api;

import com.commerce.common.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 커머스 API 실행 앱이다. */
@SpringBootApplication(scanBasePackages = "com.commerce")
@EntityScan("com.commerce")
@EnableJpaRepositories("com.commerce")
@Import(JpaAuditingConfig.class)
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}

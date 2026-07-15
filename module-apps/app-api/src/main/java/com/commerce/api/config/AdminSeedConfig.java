package com.commerce.api.config;

import com.commerce.member.exception.DuplicateEmailException;
import com.commerce.member.service.MemberAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정으로 주입된 관리자 계정을 기동 시 시딩한다.
 *
 * <p>관리자 계정 관리 API를 두지 않는 대신(REQUIREMENTS.md 범위) 자격증명을 설정(환경변수
 * {@code AUTH_ADMIN_EMAIL}·{@code AUTH_ADMIN_PASSWORD}·{@code AUTH_ADMIN_NAME})으로 받아 없으면 생성한다.
 * 같은 이메일의 활성 회원이 이미 있으면 건너뛰고(재기동 멱등), 설정이 없으면 시딩하지 않는다.
 */
@Configuration
public class AdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedConfig.class);

    @Bean
    @ConditionalOnProperty(
            prefix = "auth.admin",
            name = {"email", "password"})
    ApplicationRunner adminAccountSeeder(
            MemberAppender memberAppender,
            @Value("${auth.admin.email}") String email,
            @Value("${auth.admin.password}") String password,
            @Value("${auth.admin.name:운영 관리자}") String name) {
        return args -> {
            try {
                memberAppender.registerAdmin(email, name, password);
                log.info("관리자 계정을 시딩했다: {}", email);
            } catch (DuplicateEmailException e) {
                // 이미 시딩된 계정 — 재기동 멱등
            }
        };
    }
}

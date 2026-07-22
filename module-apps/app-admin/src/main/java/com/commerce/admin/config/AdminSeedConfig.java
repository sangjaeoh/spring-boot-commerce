package com.commerce.admin.config;

import com.commerce.member.application.provided.MemberAppender;
import com.commerce.member.domain.DuplicateEmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 관리자 계정 기동 시딩을 배선하는 설정이다. */
@Configuration
public class AdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedConfig.class);

    /** 설정으로 주입된 자격증명의 관리자를 기동 시 만드는 러너를 공급한다. 재기동에 멱등하다. */
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

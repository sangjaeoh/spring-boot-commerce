package com.commerce.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing을 켜 {@code createdAt}·{@code updatedAt}을 자동 기록하게 한다. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}

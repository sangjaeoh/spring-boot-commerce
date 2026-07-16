package com.commerce.product;

import com.commerce.jpa.config.JpaAuditingConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** {@code @DataJpaTest}가 컨텍스트 루트로 쓰는 최소 부트 설정이다. Auditing과 고정 Clock을 함께 켠다. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(JpaAuditingConfig.class)
class PersistenceTestConfig {

    static final Instant FIXED_NOW = Instant.parse("2025-06-15T00:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
}

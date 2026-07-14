package com.commerce.cart;

import com.commerce.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** {@code @DataJpaTest}가 컨텍스트 루트로 쓰는 최소 부트 설정이다. Auditing을 함께 켠다. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(JpaAuditingConfig.class)
class PersistenceTestConfig {}

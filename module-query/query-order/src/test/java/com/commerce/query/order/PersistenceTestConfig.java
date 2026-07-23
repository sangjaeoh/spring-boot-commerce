package com.commerce.query.order;

import com.commerce.common.jpa.config.JpaAuditingConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

/** {@code @DataJpaTest}가 컨텍스트 루트로 쓰는 최소 부트 설정이다. member·order 두 도메인의 엔티티를 스캔한다. */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = {"com.commerce.domain.member.domain", "com.commerce.domain.order.domain"})
@Import(JpaAuditingConfig.class)
class PersistenceTestConfig {}

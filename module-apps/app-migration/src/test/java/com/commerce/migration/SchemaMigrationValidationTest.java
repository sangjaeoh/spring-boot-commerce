package com.commerce.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 전 도메인 스키마를 한 PostgreSQL에 마이그레이션하고, 모든 도메인 엔티티를 {@code ddl-auto=validate}로
 * 부팅해 마이그레이션 DDL과 엔티티 매핑이 정합함을 검증한다(#5의 목표 — 전 도메인 한 DB validate).
 *
 * <p>스키마별 Flyway를 {@link SchemaFlywayFactory}가 먼저 실행하고, 그 뒤 하나의 EMF가 7개 도메인 엔티티를
 * 한 번에 validate한다. Boot 퍼시스턴스 슬라이스로는 7개 독립 스키마의 선행 실행을 표현할 수 없어 수동 배선한다.
 */
@Testcontainers
class SchemaMigrationValidationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    @DisplayName("전 스키마 마이그레이션 후 모든 도메인 엔티티가 validate를 통과한다")
    void allSchemasMigrateAndEntitiesValidate() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        SchemaFlywayFactory.migrateAll(dataSource);

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.commerce");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        // Spring Boot 기본 JPA 오토컨피그와 동일한 물리 네이밍(camelCase→snake_case)을 건다 — 수동 EMF라
        // 도메인 테스트(@DataJpaTest)가 자동으로 얻는 전략을 명시해야 created_at 등 파생 컬럼명이 DDL과 맞는다.
        emfBean.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto",
                "validate",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        emfBean.afterPropertiesSet(); // validate 실행 — 엔티티↔DDL 불일치면 예외로 실패한다

        try {
            EntityManagerFactory emf = emfBean.getNativeEntityManagerFactory();
            Set<String> entityNames = emf.getMetamodel().getEntities().stream()
                    .map(EntityType::getName)
                    .collect(Collectors.toSet());
            // 7개 도메인의 애그리거트 루트가 모두 스캔·검증됐는지 확인(빈 metamodel의 헛통과 방지).
            assertThat(entityNames).contains("Member", "Product", "Stock", "Cart", "Coupon", "Order", "Payment");
        } finally {
            emfBean.destroy();
        }
    }
}

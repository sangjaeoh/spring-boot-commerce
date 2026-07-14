package com.commerce.migration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * app-migration 앱을 실 PostgreSQL 위에 부팅해 전 도메인 스키마 마이그레이션·엔티티 정합을 검증한다.
 *
 * <p>부팅 자체가 실 배선(DataSource 오토컨피그 → {@code ApplicationRunner} → {@code SchemaFlywayFactory.migrateAll})을
 * 태운다. 이어 (1) 7개 스키마 대표 테이블 존재와 (2) 전 도메인 엔티티가 마이그레이션 DDL에 validate됨을 확인한다.
 * validate는 테이블·컬럼·타입 정합만 본다 — 인덱스·유니크·{@code @Version}의 의미 검증은 도메인별 슬라이스 테스트가 소유한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MigrationApplicationTest {

    private static final List<String> SCHEMA_TABLES = List.of(
            "member.member",
            "product.product",
            "stock.stock",
            "cart.cart",
            "coupon.coupon",
            "ordering.orders",
            "payment.payment");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private final DataSource dataSource;

    MigrationApplicationTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    @DisplayName("앱 부팅이 전 스키마 마이그레이션을 실행한다")
    void appBootRunsAllSchemaMigrations() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (String qualifiedTable : SCHEMA_TABLES) {
                String[] parts = qualifiedTable.split("\\.");
                assertThat(tableExists(connection, parts[0], parts[1]))
                        .as(qualifiedTable)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("전 도메인 엔티티(루트·자식)가 마이그레이션 DDL에 validate된다")
    void allDomainEntitiesValidateAgainstMigratedSchema() {
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.commerce");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        // Spring Boot 기본 물리 네이밍(camelCase→snake_case)을 건다 — created_at 등 파생 컬럼명이 DDL과 맞는다.
        // 현 엔티티는 컬럼·테이블명이 명시(@Column/@Table) 지배라 암시적 네이밍 전략은 검증 표면이 없어 생략한다.
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
            // 7개 루트 + 자식 엔티티까지 스캔·검증됐는지 확인(스캔 누락·빈 metamodel 헛통과 방지).
            assertThat(entityNames)
                    .contains(
                            "Member",
                            "Product",
                            "ProductVariant",
                            "Stock",
                            "Cart",
                            "CartItem",
                            "Coupon",
                            "IssuedCoupon",
                            "Order",
                            "OrderLine",
                            "Payment");
        } finally {
            emfBean.destroy();
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(null, schema, table, null)) {
            return tables.next();
        }
    }
}

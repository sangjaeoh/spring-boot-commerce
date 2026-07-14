package com.commerce.migration;

import com.commerce.jpa.migration.SchemaFlywayFactory;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 전 도메인 스키마의 Flyway 마이그레이션을 한 DB에 실행하는 독립 앱이다.
 *
 * <p>데이터소스는 {@code spring.datasource.*}(환경 변수·실행 인자)로 주입한다. Boot 기본 Flyway
 * 오토컨피그는 쓰지 않고({@code spring.flyway.enabled=false}), 스키마별 실행을 {@link SchemaFlywayFactory}가 담당한다.
 */
@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }

    @Bean
    ApplicationRunner migrateAllSchemas(DataSource dataSource) {
        return args -> SchemaFlywayFactory.migrateAll(dataSource);
    }
}

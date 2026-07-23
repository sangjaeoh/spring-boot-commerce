package com.commerce.app.migration;

import com.commerce.common.jpa.migration.SchemaFlywayFactory;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 전 도메인 스키마의 Flyway 마이그레이션을 한 DB에 실행하는 독립 앱이다. */
@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }

    /** 기동 시 등록된 모든 도메인 스키마를 마이그레이션하는 러너를 만든다. */
    @Bean
    ApplicationRunner migrateAllSchemas(DataSource dataSource) {
        return args -> SchemaFlywayFactory.migrateAll(dataSource);
    }
}

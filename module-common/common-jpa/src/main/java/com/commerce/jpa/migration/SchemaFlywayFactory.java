package com.commerce.jpa.migration;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** 도메인 스키마별로 Flyway를 실행하는 팩토리다. */
public final class SchemaFlywayFactory {

    // 등록된 스키마(도메인 + 교차 관심사 messaging 아웃박스). db/migration/{name}/ 로케이션 규약과 1:1 대응한다.
    private static final List<String> SCHEMAS = List.of(
            "member",
            "product",
            "stock",
            "cart",
            "coupon",
            "ordering",
            "payment",
            "wishlist",
            "review",
            "inquiry",
            "messaging");

    private SchemaFlywayFactory() {}

    /** 한 스키마의 Flyway를 구성한다. 기본 스키마·로케이션·이력 배치를 그 스키마로 고정한다. */
    // 도메인 마이그레이션이 모두 V1__이라 로케이션을 합치면 버전이 충돌한다. 스키마마다 독립 인스턴스를
    // 두어야 각자 flyway_schema_history로 버전을 따로 추적한다.
    private static Flyway forSchema(DataSource dataSource, String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/" + schema)
                .load();
    }

    /** 등록된 모든 스키마를 순서대로 마이그레이션한다. */
    public static void migrateAll(DataSource dataSource) {
        for (String schema : SCHEMAS) {
            forSchema(dataSource, schema).migrate();
        }
    }
}

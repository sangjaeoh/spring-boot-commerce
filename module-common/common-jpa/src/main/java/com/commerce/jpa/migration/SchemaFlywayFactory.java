package com.commerce.jpa.migration;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * 도메인 스키마별로 Flyway를 실행한다.
 *
 * <p>모든 도메인 마이그레이션이 {@code V1__}이라 단일 Flyway로 7개 로케이션을 합치면 버전이 충돌한다.
 * 스키마마다 독립 Flyway 인스턴스를 두어 각자 {@code flyway_schema_history}로 버전을 추적한다 — 스키마
 * 경계가 이력까지 분리해 물리 FK 없는 도메인 경계와 정합하며 MSA 분리 시 떼어낼 이력이 얽히지 않는다.
 */
public final class SchemaFlywayFactory {

    /** 등록된 도메인 스키마. {@code db/migration/{name}/} 로케이션 규약과 1:1 대응한다. */
    private static final List<String> SCHEMAS =
            List.of("member", "product", "stock", "cart", "coupon", "ordering", "payment");

    private SchemaFlywayFactory() {}

    /** 한 스키마의 Flyway를 구성한다. 기본 스키마·로케이션·이력 배치를 그 스키마로 고정한다. */
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

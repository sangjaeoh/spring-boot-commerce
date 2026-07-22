plugins {
    id("convention.infra-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    // 아웃박스 행 ID(UUIDv7) 생성.
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-event"))
    implementation(libs.spring.context)
    // 아웃박스 저장이 발행 트랜잭션에 참여한다(JdbcTemplate·트랜잭션 동기화 검사).
    implementation(libs.spring.jdbc)
    // 이벤트 페이로드 JSON 직렬화. 런타임은 실행 앱(웹 스타터)이 제공하므로 compileOnly.
    compileOnly(libs.jackson.databind)
    // 아웃박스 릴레이(재전달 구현) — 실패 격리 로그, 프로퍼티 게이트, 분산 락 애노테이션(배선은 앱 소유).
    implementation(libs.slf4j.api)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.shedlock.spring)

    testImplementation(libs.spring.boot.starter.test)
    // DLQ 시나리오 테스트가 릴레이를 직접 구동하며 이벤트 페이로드 직렬화 런타임을 요구한다.
    testImplementation(libs.jackson.databind)
    // 아웃박스 마이그레이션(V2 전환) 검증 — 실 PostgreSQL 위에서 Flyway를 대상 버전까지 직접 실행한다.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

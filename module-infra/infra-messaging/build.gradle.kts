plugins {
    id("convention.infra-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    // 아웃박스 행 ID(UUIDv7) 생성.
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-messaging"))
    implementation(libs.spring.context)
    // 아웃박스 저장이 발행 트랜잭션에 참여한다(JdbcTemplate·트랜잭션 동기화 검사).
    implementation(libs.spring.jdbc)
    // 이벤트 페이로드 JSON 직렬화. 런타임은 실행 앱(웹 스타터)이 제공하므로 compileOnly.
    compileOnly(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

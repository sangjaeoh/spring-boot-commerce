plugins {
    id("convention.app-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    // SecurityConfig가 JWT 필터·엔트리포인트를, AuthConfig가 토큰 코덱을 코드 참조한다.
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-common:common-web"))
    implementation(project(":module-domains:domain-shared"))
    implementation(project(":module-domains:domain-member"))
    implementation(project(":module-domains:domain-product"))
    implementation(project(":module-domains:domain-stock"))
    implementation(project(":module-domains:domain-coupon"))
    implementation(project(":module-domains:domain-order"))
    implementation(project(":module-domains:domain-payment"))
    implementation(project(":module-domains:domain-inquiry"))
    implementation(libs.spring.boot.starter.web)
    // 무상태 JWT SecurityFilterChain(SecurityConfig)이 조립하는 시큐리티 런타임(오토컨피그 포함).
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // 스테레오타입 빈만 조립한다(코드 참조 없음): 환불 PG 취소 어댑터·이미지 저장 어댑터·in-process 발행
    // transport·멱등 키 저장소·JDBC 드라이버.
    runtimeOnly(project(":module-external:external-payment"))
    runtimeOnly(project(":module-external:external-storage"))
    runtimeOnly(project(":module-infra:infra-messaging"))
    runtimeOnly(project(":module-infra:infra-redis"))
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

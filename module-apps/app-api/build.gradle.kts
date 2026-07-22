plugins {
    id("convention.app-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-common:common-auth"))
    // 컨트롤러가 인증 주체(AuthUser)를 코드 참조한다. 경계 웹 빈(ProblemDetail 핸들러·인증/멱등 필터·
    // 아규먼트 리졸버)은 스테레오타입 스캔으로 조립된다.
    implementation(project(":module-common:common-web"))
    implementation(project(":module-domains:domain-shared"))
    implementation(project(":module-domains:domain-member"))
    implementation(project(":module-domains:domain-product"))
    implementation(project(":module-domains:domain-stock"))
    implementation(project(":module-domains:domain-cart"))
    implementation(project(":module-domains:domain-coupon"))
    implementation(project(":module-domains:domain-order"))
    implementation(project(":module-domains:domain-payment"))
    implementation(project(":module-domains:domain-wishlist"))
    implementation(project(":module-domains:domain-review"))
    implementation(libs.spring.boot.starter.web)
    // 무상태 JWT SecurityFilterChain(SecurityConfig)이 조립하는 시큐리티 런타임(오토컨피그 포함).
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    // 스케줄 스윕의 @SchedulerLock·@EnableSchedulerLock. LockProvider 구현은 infra-redis가 배선한다.
    implementation(libs.shedlock.spring)

    // 스테레오타입 빈만 조립한다(코드 참조 없음): 결제 어댑터·in-process 발행 transport·멱등 키 저장소·JDBC 드라이버.
    runtimeOnly(project(":module-external:external-payment"))
    runtimeOnly(project(":module-infra:infra-messaging"))
    runtimeOnly(project(":module-infra:infra-redis"))
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    // 웹 통합 테스트가 공유 Redis의 로그인 레이트리밋 카운터를 테스트 간 비운다(StringRedisTemplate).
    testImplementation(libs.spring.boot.starter.data.redis)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

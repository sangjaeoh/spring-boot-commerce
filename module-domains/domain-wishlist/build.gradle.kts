plugins {
    id("convention.domain-module")
}

// 재입고 알림 소비의 동기 조회 간선 — variantId→productId·상품명(product), 수신자 이메일(member).
domainModule {
    dependsOnDomains.set(setOf("domain-product", "domain-member"))
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    // 소비 이벤트 record(StockRestocked)는 발행 도메인의 이벤트 모듈이 소유한다.
    implementation(project(":module-events:event-stock"))
    implementation(project(":module-domains:domain-product"))
    implementation(project(":module-domains:domain-member"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.spring.boot.starter.flyway)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.postgresql)
}

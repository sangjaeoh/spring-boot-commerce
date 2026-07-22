plugins {
    id("convention.app-module")
}

dependencies {
    implementation(project(":module-common:common-jpa"))
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // 도메인은 마이그레이션 리소스(db/migration/{schema})와 엔티티(검증용)만 필요하다 — 도메인 코드
    // 컴파일 의존은 없으므로 runtimeOnly로 둔다.
    runtimeOnly(project(":module-domains:domain-member"))
    runtimeOnly(project(":module-domains:domain-product"))
    runtimeOnly(project(":module-domains:domain-stock"))
    runtimeOnly(project(":module-domains:domain-cart"))
    runtimeOnly(project(":module-domains:domain-coupon"))
    runtimeOnly(project(":module-domains:domain-order"))
    runtimeOnly(project(":module-domains:domain-payment"))
    runtimeOnly(project(":module-domains:domain-wishlist"))
    runtimeOnly(project(":module-domains:domain-review"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

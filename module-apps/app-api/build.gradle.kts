plugins {
    id("convention.app-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-domains:domain-member"))
    implementation(project(":module-domains:domain-product"))
    implementation(project(":module-domains:domain-stock"))
    implementation(project(":module-domains:domain-cart"))
    implementation(project(":module-domains:domain-coupon"))
    implementation(project(":module-domains:domain-order"))
    implementation(project(":module-domains:domain-payment"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)

    // 스테레오타입 빈만 조립한다(코드 참조 없음): 결제 어댑터·in-process 발행 transport·경계 웹 빈
    // (ProblemDetail 핸들러·멱등 필터·저장소)·JDBC 드라이버.
    runtimeOnly(project(":module-external:external-payment"))
    runtimeOnly(project(":module-infra:infra-messaging"))
    runtimeOnly(project(":module-common:common-web"))
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

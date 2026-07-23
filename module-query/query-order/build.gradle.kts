plugins {
    id("convention.query-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    // Money가 경계 시그니처(Info)에 등장해 소비자에게 재노출된다.
    api(project(":module-domains:domain-shared"))
    implementation(project(":module-domains:domain-member"))
    implementation(project(":module-domains:domain-order"))

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

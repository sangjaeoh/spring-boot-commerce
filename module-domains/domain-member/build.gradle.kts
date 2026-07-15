plugins {
    id("convention.domain-module")
}

dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))

    // 패스워드 bcrypt 해시. Spring Security 프레임워크가 아니라 crypto 라이브러리 단독이다(REQUIREMENTS.md 제약·전제).
    implementation(libs.spring.security.crypto)
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

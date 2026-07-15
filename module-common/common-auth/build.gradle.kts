plugins {
    id("convention.common-module")
}

dependencies {
    // JWT(HS256) 발급·검증 원자재. 웹 필터는 common-web이 소유한다(docs/architecture.md common 모듈 배치).
    implementation(libs.nimbus.jose.jwt)

    testImplementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

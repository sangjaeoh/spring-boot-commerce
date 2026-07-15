plugins {
    id("convention.infra-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-web"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.starter.data.redis)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.redis)
    testRuntimeOnly(libs.junit.platform.launcher)
}

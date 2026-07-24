plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.data.redis)
    implementation(libs.jackson.databind)
    implementation(libs.micrometer.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.lettuce.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

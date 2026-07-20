plugins {
    id("convention.domain-module")
}

dependencies {
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

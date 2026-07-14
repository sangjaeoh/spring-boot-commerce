plugins {
    id("convention.infra-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-messaging"))
    implementation(libs.spring.context)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

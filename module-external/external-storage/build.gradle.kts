plugins {
    id("convention.external-module")
}

externalModule {
    targetDomain.set("domain-product")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-domains:domain-product"))
    implementation(libs.spring.context)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

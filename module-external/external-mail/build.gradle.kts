plugins {
    id("convention.external-module")
}

externalModule {
    targetDomain.set("domain-member")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-domains:domain-member"))
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
}

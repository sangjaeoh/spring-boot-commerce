plugins {
    id("convention.infra-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-common:common-web"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.starter.data.redis)
    // 스케줄 스윕 분산 락의 Redis LockProvider(ShedLock). 애노테이션 배선은 app-batch 소유다.
    implementation(libs.shedlock.provider.redis.spring)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.redis)
    testRuntimeOnly(libs.junit.platform.launcher)
}

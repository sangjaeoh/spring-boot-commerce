plugins {
    id("convention.domain-module")
}

dependencies {
    // Money가 이 모듈의 경계 시그니처(Info·명령 입력)에 등장해 소비자에게 재노출된다.
    api(project(":module-domains:domain-shared"))
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-event"))
    // 발행 이벤트 record(OrderPaid)는 이벤트 모듈이 소유한다.
    implementation(project(":module-events:event-order"))
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

plugins {
    base
}

// build-logic은 pluginManagement 포함 빌드라 그 테스트(경계 강제 로직 검증)가 루트 `build`에
// 자동으로 묶이지 않는다. 루트 게이트가 함께 돌리도록 명시적으로 연결한다.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

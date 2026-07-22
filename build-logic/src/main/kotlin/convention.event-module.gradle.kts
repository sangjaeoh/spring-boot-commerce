plugins {
    id("convention.java-common")
}

restrictProjectDependencies("event-module") { dependencyPath ->
    dependencyPath == ":module-common:common-event"
}

// 이벤트 record는 프레임워크 컴파일 의존이 없어야 한다 — 언어 표준·JSpecify·common-event 계약만 쓴다
// (docs/architecture.md 이벤트 모듈 구조). 프레임워크 그룹은 main 클래스패스 해석 시점에 차단한다.
val frameworkGroupPrefixes =
    listOf("org.springframework", "jakarta", "org.hibernate", "com.fasterxml.jackson", "tools.jackson")

listOf("compileClasspath", "runtimeClasspath").forEach { classpath ->
    configurations.named(classpath) {
        resolutionStrategy.eachDependency {
            if (frameworkGroupPrefixes.any { requested.group.startsWith(it) }) {
                throw GradleException(
                    "event-module 은(는) 프레임워크에 의존하지 않는다: ${requested.group}:${requested.name} " +
                        "(docs/architecture.md 이벤트 모듈 구조)",
                )
            }
        }
    }
}

plugins {
    id("convention.java-common")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// 이벤트 모듈 의존은 저장소 분리 전환 시 명시 등록으로만 허용한다 — 전환 실물이 나타날 때 등록 확장을 추가한다.
restrictProjectDependencies("query-module") { dependencyPath ->
    dependencyPath.startsWith(":module-domains:") || dependencyPath.startsWith(":module-common:")
}

dependencies {
    "implementation"(
        platform(
            "org.springframework.boot:spring-boot-dependencies:${
                libsCatalog.findVersion("spring-boot").get().requiredVersion
            }",
        ),
    )
    "implementation"(libsCatalog.findLibrary("spring-boot-starter-data-jpa").get())
}

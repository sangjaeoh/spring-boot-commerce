import org.gradle.api.provider.SetProperty

plugins {
    id("convention.java-common")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

interface DomainModuleExtension {
    // 이 도메인이 의존을 명시 등록한 다른 도메인 모듈 이름("domain-{name}") 간선. 등록 간선은 비순환이어야 한다.
    val dependsOnDomains: SetProperty<String>
}

val domainModule = extensions.create("domainModule", DomainModuleExtension::class.java)

// 등록 간선을 루트에 모은다 — 전 프로젝트 구성 완료 후 비순환을 한 번 검증한다(현재 등록 간선 0건).
val extra = rootProject.extensions.extraProperties

@Suppress("UNCHECKED_CAST")
val domainEdges: MutableMap<String, Set<String>> =
    if (extra.has("domainModuleEdges")) {
        extra.get("domainModuleEdges") as MutableMap<String, Set<String>>
    } else {
        mutableMapOf<String, Set<String>>().also { extra.set("domainModuleEdges", it) }
    }

if (!extra.has("domainModuleAcyclicCheckRegistered")) {
    extra.set("domainModuleAcyclicCheckRegistered", true)
    gradle.projectsEvaluated { ensureAcyclicDomainEdges(domainEdges) }
}

// dependsOnDomains는 이 스크립트가 적용된 뒤 소비 모듈의 build.gradle.kts가 설정하므로,
// 전체 스크립트 평가가 끝난 뒤(afterEvaluate)에만 최종 값을 알 수 있다.
afterEvaluate {
    val registeredDomains = domainModule.dependsOnDomains.getOrElse(emptySet())
    domainEdges[project.name] = registeredDomains.toSet()
    restrictProjectDependencies("domain-module") { dependencyPath ->
        dependencyPath == ":module-domains:domain-shared" ||
            dependencyPath.startsWith(":module-common:") ||
            dependencyPath.startsWith(":module-events:") ||
            registeredDomains.any { dependencyPath == ":module-domains:$it" }
    }
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

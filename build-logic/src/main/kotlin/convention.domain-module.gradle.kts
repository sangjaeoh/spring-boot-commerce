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
    // Q타입 생성(QueryDSL apt). 생성 코드가 참조하는 querydsl-core를 컴파일 클래스패스에 둔다.
    // apt classifier(jakarta)는 TOML 미지원이라 문자열 좌표로 선언한다. 프로세서 경로 의존은 pom 전이로 해석된다.
    "implementation"(libsCatalog.findLibrary("querydsl-core").get())
    "annotationProcessor"(
        "io.github.openfeign.querydsl:querydsl-apt:${
            libsCatalog.findVersion("querydsl").get().requiredVersion
        }:jakarta",
    )
}

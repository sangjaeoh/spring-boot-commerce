import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * 모든 구성(configuration)에 추가되는 프로젝트 의존을 [isAllowed]로 검사해,
 * 화이트리스트를 벗어나면 의존성 해석 이전(구성 시점)에 빌드를 깨뜨린다.
 * `api`·`implementation`만 검사하면 `compileOnly`·`testImplementation` 등으로 타 도메인
 * 컴파일 의존이 가능하므로 구성 전수를 검사한다.
 *
 * docs/architecture.md 모듈 지도의 "경계를 컴파일 의존성 자체로 강제한다"에 대응한다.
 */
fun Project.restrictProjectDependencies(ownerDescription: String, isAllowed: (String) -> Boolean) {
    configurations.configureEach {
        dependencies.withType(ProjectDependency::class.java).configureEach {
            val dependencyPath = path
            if (!isAllowed(dependencyPath)) {
                throw GradleException(
                    "$ownerDescription 은(는) 이 의존을 가질 수 없다: $dependencyPath " +
                        "(docs/architecture.md 모듈 지도)",
                )
            }
        }
    }
}

/**
 * 도메인 간 등록 간선(도메인명 → 의존 도메인명 집합)에서 순환을 검출해, 있으면 구성 시점에 빌드를 깨뜨린다.
 *
 * docs/architecture.md 모듈 지도의 "명시 등록한 다른 domain-{name} 모듈만 의존 가능" 간선이 순환하면
 * 도메인 경계가 상호 재귀로 무너지므로 등록 자체를 거부한다.
 */
fun ensureAcyclicDomainEdges(edges: Map<String, Collection<String>>) {
    val visiting = LinkedHashSet<String>()
    val done = HashSet<String>()

    fun visit(node: String) {
        if (node in done) {
            return
        }
        if (!visiting.add(node)) {
            val cycle = visiting.dropWhile { it != node } + node
            throw GradleException(
                "도메인 간선 등록에 순환이 있다: ${cycle.joinToString(" -> ")} (docs/architecture.md 모듈 지도)",
            )
        }
        edges[node].orEmpty().forEach { visit(it) }
        visiting.remove(node)
        done.add(node)
    }

    edges.keys.forEach { visit(it) }
}

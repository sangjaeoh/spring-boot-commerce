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

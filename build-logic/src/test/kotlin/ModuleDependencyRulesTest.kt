import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * [restrictProjectDependencies]가 컴파일 구성(`api`·`implementation`)만이 아니라 모든 구성에서
 * 화이트리스트 밖 프로젝트 의존을 거부하는지 검증한다.
 */
class ModuleDependencyRulesTest {

    private fun restrictedProject(): Project {
        val root = ProjectBuilder.builder().withName("root").build()
        ProjectBuilder.builder().withName("forbidden").withParent(root).build()
        ProjectBuilder.builder().withName("allowed").withParent(root).build()
        val owner = ProjectBuilder.builder().withName("owner").withParent(root).build()
        owner.plugins.apply("java-library")
        owner.restrictProjectDependencies("owner-module") { dependencyPath -> dependencyPath == ":allowed" }
        return owner
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "api",
            "implementation",
            "compileOnly",
            "compileOnlyApi",
            "runtimeOnly",
            "testImplementation",
            "testRuntimeOnly",
            "annotationProcessor",
        ],
    )
    fun rejectsProjectDependencyOutsideWhitelistInEveryConfiguration(configurationName: String) {
        val owner = restrictedProject()

        val exception = assertThrows<GradleException> {
            owner.dependencies.add(configurationName, owner.dependencies.project(mapOf("path" to ":forbidden")))
            owner.configurations.getByName(configurationName).dependencies.toList()
        }

        assertTrue(exception.message!!.contains(":forbidden"))
    }

    @Test
    fun allowsWhitelistedProjectAndExternalLibraryDependencies() {
        val owner = restrictedProject()

        owner.dependencies.add("testImplementation", owner.dependencies.project(mapOf("path" to ":allowed")))
        owner.dependencies.add("implementation", "org.example:library:1.0")

        // 검사 콜백은 의존 실현 시 실행된다 — 순회가 예외 없이 끝나면 통과다.
        owner.configurations.getByName("testImplementation").dependencies.toList()
        owner.configurations.getByName("implementation").dependencies.toList()
    }

    @Test
    fun acceptsAcyclicDomainEdgeRegistration() {
        // 검출 함수는 순환이 없으면 예외 없이 끝난다 — 간선 0건(현 상태)과 선형 간선을 함께 확인한다.
        ensureAcyclicDomainEdges(emptyMap())
        ensureAcyclicDomainEdges(
            mapOf(
                "domain-a" to setOf("domain-b"),
                "domain-b" to setOf("domain-c"),
                "domain-c" to emptySet(),
            ),
        )
    }

    @Test
    fun rejectsCyclicDomainEdgeRegistrationAtConfigurationTime() {
        val exception = assertThrows<GradleException> {
            ensureAcyclicDomainEdges(
                mapOf(
                    "domain-a" to setOf("domain-b"),
                    "domain-b" to setOf("domain-a"),
                ),
            )
        }

        assertTrue(exception.message!!.contains("domain-a"))
        assertTrue(exception.message!!.contains("domain-b"))
    }
}

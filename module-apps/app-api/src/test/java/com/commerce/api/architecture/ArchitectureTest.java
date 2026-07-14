package com.commerce.api.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md의 "빌드가 강제하는 불변식" 중 컨벤션 플러그인(컴파일 시점)이 잡지 못하는 항목을
 * 컴파일된 클래스 그래프에서 검증한다. 앞 두 규칙은 app-api가 도메인을 의존하는 P2부터 비공허하게 활성화돼,
 * {@code ..facade..}·{@code ..event.listener..}가 생 엔티티({@code @Entity}·{@code @MappedSuperclass})나
 * 도메인 리포지토리에 의존하면 실패한다. 세 번째 규칙은 패키지와 무관하게, 어떤 클래스든 소프트삭제 엔티티
 * 리포지토리의 base finder를 직접 호출하면 실패한다.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.commerce");

    // 삭제 여부를 거르지 않아 소프트삭제 엔티티 리포지토리에 금지되는 base JpaRepository finder(findAll·count의
    // QueryByExampleExecutor 오버로드도 이름으로 함께 잡힌다). deprecated 별칭 getOne·getById는 대체 API
    // getReferenceById가 이미 금지 대상이라 제외한다. architecture.md 리포지토리 접근 범위.
    private static final Set<String> BASE_FINDERS =
            Set.of("findById", "existsById", "findAll", "findAllById", "count", "getReferenceById");

    // deletedAt 필드를 가진 @Entity의 FQN 집합. 리포지토리→엔티티 판정은 이 집합만으로 하며 Spring Data 타입 계층
    // 해석에 의존하지 않는다. softDeleteEntityNames가 CLASSES를 순회하므로 반드시 CLASSES 선언 뒤에 둔다.
    private static final Set<String> SOFT_DELETE_ENTITY_NAMES = softDeleteEntityNames();

    @Test
    void jpaEntitiesAreOnlyAccessedWithinTheirOwnDomainModule() {
        DescribedPredicate<CanBeAnnotated> jpaMapped = annotatedWith("jakarta.persistence.Entity")
                .or(annotatedWith("jakarta.persistence.MappedSuperclass"))
                .as("@Entity 또는 @MappedSuperclass 매핑 클래스");
        ArchRule rule = noClasses()
                .that()
                // @Entity를 정당하게 참조하는 4개 패키지만 제외한다(entity 상호참조, Info.from(Entity),
                // service·repository). port·event·exception은 값 타입만 참조하므로(PaymentGateway→enum,
                // OrderPaid→UUID) 제외하지 않는다 — 제외하면 앱 event/listener 등이 생 엔티티를 참조해도
                // 못 잡는 구멍이 생긴다.
                .resideOutsideOfPackages("..entity..", "..info..", "..service..", "..repository..")
                .should()
                .dependOnClassesThat(jpaMapped)
                .because("JPA 엔티티(@Entity·@MappedSuperclass)는 소유 도메인 모듈 내부에서만 접근한다"
                        + " — architecture.md 경계 원칙. 값 타입(enum·record·@Embeddable VO)은 경계 계약으로 통과 허용");

        rule.check(CLASSES);
    }

    @Test
    void appsDoNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.commerce.api..")
                .should()
                .dependOnClassesThat()
                // 도메인 리포지토리(com.commerce..repository..)만 대상이다. 프레임워크의
                // org.springframework.data.jpa.repository.config(@EnableJpaRepositories 배선)까지
                // "..repository.."가 잡지 않게 접두를 고정한다.
                .resideInAPackage("com.commerce..repository..")
                .because("apps는 도메인 리포지토리에 직접 접근하지 않는다 — architecture.md 리포지토리 접근 범위");

        rule.check(CLASSES);
    }

    @Test
    void softDeleteRepositoryBaseFindersAreNotCalledDirectly() {
        DescribedPredicate<JavaMethodCall> baseFinderOnSoftDeleteRepository =
                new DescribedPredicate<>("소프트삭제 엔티티 리포지토리에 삭제 여부를 거르지 않는 base finder 직접 호출") {
                    @Override
                    public boolean test(JavaMethodCall call) {
                        return BASE_FINDERS.contains(call.getName()) && managesSoftDeleteEntity(call.getTargetOwner());
                    }
                };

        ArchRule rule = noClasses()
                .should()
                .callMethodWhere(baseFinderOnSoftDeleteRepository)
                .because("소프트삭제(deletedAt) 엔티티 리포지토리는 삭제 여부를 거르지 않는 base JpaRepository finder"
                        + " 직접 호출을 금지한다 — 삭제분을 거르지 않으므로. 활성-only 파생 쿼리(...DeletedAtIsNull)를 쓴다."
                        + " 금지 목록은 architecture.md 리포지토리 접근 범위");

        rule.check(CLASSES);
    }

    @Test
    void softDeleteEntitiesAreDetected() {
        // 규칙의 변별력은 SOFT_DELETE_ENTITY_NAMES가 채워져 있음에 달렸다 — 비면 규칙이 공허하게 통과하고
        // (failOnEmptyShould는 .that() 필터가 없어 못 잡는다) 실제 위반을 놓친다. 감지 회귀를 여기서 실패시킨다.
        assertEquals(
                Set.of("com.commerce.member.entity.Member", "com.commerce.product.entity.Product"),
                SOFT_DELETE_ENTITY_NAMES);
    }

    // 리포지토리의 제네릭 상위 인터페이스(JpaRepository<E, ID> 등) 타입 인자가 소프트삭제 엔티티면 참이다.
    private static boolean managesSoftDeleteEntity(JavaClass repository) {
        for (JavaType superInterface : repository.getInterfaces()) {
            if (superInterface instanceof JavaParameterizedType parameterized) {
                for (JavaType typeArgument : parameterized.getActualTypeArguments()) {
                    if (SOFT_DELETE_ENTITY_NAMES.contains(
                            typeArgument.toErasure().getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<String> softDeleteEntityNames() {
        Set<String> names = new HashSet<>();
        for (JavaClass candidate : CLASSES) {
            if (candidate.isAnnotatedWith("jakarta.persistence.Entity") && hasDeletedAtField(candidate)) {
                names.add(candidate.getName());
            }
        }
        return Set.copyOf(names);
    }

    private static boolean hasDeletedAtField(JavaClass entity) {
        return entity.getAllFields().stream().anyMatch(field -> field.getName().equals("deletedAt"));
    }
}

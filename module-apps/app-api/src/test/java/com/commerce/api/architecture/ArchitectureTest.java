package com.commerce.api.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.api.architecture.bypass.service.EntityReferencingFixture;
import com.commerce.member.entity.Member;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md의 "빌드가 강제하는 불변식" 중 컨벤션 플러그인(컴파일 시점)이 잡지 못하는 항목을
 * 컴파일된 클래스 그래프에서 검증한다. 앞 두 규칙은 app-api가 도메인을 의존하는 P2부터 비공허하게 활성화돼,
 * {@code ..facade..}·{@code ..event.listener..}가 생 엔티티({@code @Entity}·{@code @MappedSuperclass})나
 * 도메인 리포지토리에 의존하면 실패한다. 세 번째 규칙은 패키지와 무관하게, 어떤 클래스든 소프트삭제 엔티티
 * 리포지토리의 base finder를 직접 호출하면 실패한다. 네 번째 규칙은 web 컨트롤러가 서로 다른 도메인의
 * service를 2개 이상 직접 의존하면 실패한다 — 크로스 도메인 조율은 facade가 소유한다(architecture.md 앱 모듈 구조).
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

    // 매핑 클래스(@Entity·@MappedSuperclass)가 사는 {base}.entity 패키지에서 파생한 소유 모듈 베이스 패키지
    // 집합 — 도메인 모듈(com.commerce.{domain})과 공통 베이스 엔티티의 common-jpa(com.commerce.jpa).
    // 엔티티 접근 면제를 이 접두로 한정한다. entityOwningBasePackages가 CLASSES를 순회하므로 CLASSES 선언 뒤에 둔다.
    private static final Set<String> ENTITY_OWNING_BASE_PACKAGES = entityOwningBasePackages();

    // 도메인 모듈 안에서 @Entity를 정당하게 참조하는 4개 서브 패키지(entity 상호참조, Info.from(Entity),
    // service·repository). port·event·exception은 값 타입만 참조하므로(PaymentGateway→enum, OrderPaid→UUID)
    // 면제하지 않는다.
    private static final Set<String> ENTITY_ACCESS_EXEMPT_SUBPACKAGES =
            Set.of("entity", "info", "service", "repository");

    @Test
    void jpaEntitiesAreOnlyAccessedWithinTheirOwnDomainModule() {
        jpaEntityAccessConfinedToDomainModules().check(CLASSES);
    }

    @Test
    void entityOwningBasePackagesAreDetected() {
        // 면제 한정의 변별력은 ENTITY_OWNING_BASE_PACKAGES가 매핑 클래스 소유 모듈만 담고 있음에 달렸다 —
        // 앱 등 다른 모듈에 매핑 클래스가 생기면 그 베이스가 면제 접두로 승격되므로 여기서 실패시킨다.
        assertEquals(
                Set.of(
                        "com.commerce.cart",
                        "com.commerce.coupon",
                        "com.commerce.jpa",
                        "com.commerce.member",
                        "com.commerce.order",
                        "com.commerce.payment",
                        "com.commerce.product",
                        "com.commerce.stock"),
                ENTITY_OWNING_BASE_PACKAGES);
    }

    @Test
    void entityAccessExemptionDoesNotCoverServicePackagesOutsideDomainModules() {
        // 도메인 밖 ...service 패키지의 생 엔티티 참조가 면제 없이 위반으로 잡히는지 고정한다.
        JavaClasses bypassAttempt = new ClassFileImporter().importClasses(EntityReferencingFixture.class, Member.class);

        assertThrows(
                AssertionError.class,
                () -> jpaEntityAccessConfinedToDomainModules().check(bypassAttempt));
    }

    // 면제는 매핑 클래스 소유 모듈의 베이스 패키지 접두로 한정한다 — 전역 패키지명 기준(..service.. 등)이면
    // 앱이 ...service 패키지를 만들어 생 엔티티를 참조해도 면제되는 우회가 생긴다.
    private static ArchRule jpaEntityAccessConfinedToDomainModules() {
        DescribedPredicate<CanBeAnnotated> jpaMapped = annotatedWith("jakarta.persistence.Entity")
                .or(annotatedWith("jakarta.persistence.MappedSuperclass"))
                .as("@Entity 또는 @MappedSuperclass 매핑 클래스");
        DescribedPredicate<JavaClass> entityOwningModuleInternal =
                new DescribedPredicate<>("매핑 클래스 소유 모듈 내부 패키지(entity·info·service·repository)") {
                    @Override
                    public boolean test(JavaClass clazz) {
                        return isEntityOwningModuleInternal(clazz.getPackageName());
                    }
                };
        return noClasses()
                .that(not(entityOwningModuleInternal))
                .should()
                .dependOnClassesThat(jpaMapped)
                .because("JPA 엔티티(@Entity·@MappedSuperclass)는 소유 도메인 모듈 내부에서만 접근한다"
                        + " — architecture.md 경계 원칙. 값 타입(enum·record·@Embeddable VO)은 경계 계약으로 통과 허용");
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

    @Test
    void controllersDependOnAtMostOneDomainServiceDirectly() {
        controllersDependOnAtMostOneDomainService().check(CLASSES);
    }

    @Test
    void domainServiceRootParsingIsCorrect() {
        // 규칙의 변별력은 도메인 service 패키지 판정의 정확성에 달렸다 — facade·info·common을 service로 오인하면
        // 규칙이 공허해지거나(도메인 0개로 항상 통과) 오탐한다. 파싱 회귀를 여기서 고정한다.
        assertEquals(Optional.of("order"), domainServiceRoot("com.commerce.order.service"));
        assertEquals(Optional.of("payment"), domainServiceRoot("com.commerce.payment.service.support"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.api.facade"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.order.info"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.auth.token"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.web.auth"));
    }

    // web 컨트롤러가 서로 다른 도메인의 service 패키지에 2개 이상 직접 의존하면 실패한다. 컨트롤러는 단일 도메인
    // service(또는 facade)에만 얇게 위임하고, 크로스 도메인 조율은 facade가 소유한다 — architecture.md 앱 모듈 구조.
    // 여러 도메인 service를 엮는 facade 자신(com.commerce.api.facade)은 web 패키지 밖이라 대상이 아니다.
    private static ArchRule controllersDependOnAtMostOneDomainService() {
        return classes()
                .that()
                .resideInAPackage("com.commerce.api.web..")
                .should(dependOnAtMostOneDomainService())
                .because("컨트롤러가 여러 도메인을 조율하면 facade로 옮긴다 — 크로스 도메인 조율은 facade가 소유한다." + " architecture.md 앱 모듈 구조");
    }

    private static ArchCondition<JavaClass> dependOnAtMostOneDomainService() {
        return new ArchCondition<>("최대 한 도메인의 service에만 직접 의존") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                Set<String> domains = new TreeSet<>();
                for (Dependency dependency : controller.getDirectDependenciesFromSelf()) {
                    domainServiceRoot(dependency.getTargetClass().getPackageName())
                            .ifPresent(domains::add);
                }
                if (domains.size() > 1) {
                    events.add(SimpleConditionEvent.violated(
                            controller,
                            controller.getName() + " 가 여러 도메인 service에 직접 의존한다: " + domains
                                    + " — 크로스 도메인 조율은 facade로 옮긴다"));
                }
            }
        };
    }

    // com.commerce.{root}.service[.*] 패키지면 도메인 루트를 반환한다. 도메인 모듈만 .service 서브 패키지를 두므로
    // (facade·info·entity·common엔 없다) 이 접미 판정으로 도메인 service 의존을 가른다.
    private static Optional<String> domainServiceRoot(String packageName) {
        String prefix = "com.commerce.";
        if (!packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] segments = packageName.substring(prefix.length()).split("\\.");
        if (segments.length >= 2 && segments[1].equals("service")) {
            return Optional.of(segments[0]);
        }
        return Optional.empty();
    }

    // 클래스가 매핑 클래스 소유 모듈의 면제 서브 패키지({base}.{entity|info|service|repository} 이하)에 있으면 참이다.
    private static boolean isEntityOwningModuleInternal(String packageName) {
        for (String basePackage : ENTITY_OWNING_BASE_PACKAGES) {
            if (!packageName.startsWith(basePackage + ".")) {
                continue;
            }
            String firstSubpackage =
                    packageName.substring(basePackage.length() + 1).split("\\.", 2)[0];
            if (ENTITY_ACCESS_EXEMPT_SUBPACKAGES.contains(firstSubpackage)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> entityOwningBasePackages() {
        Set<String> basePackages = new HashSet<>();
        for (JavaClass candidate : CLASSES) {
            if (candidate.isAnnotatedWith("jakarta.persistence.Entity")
                    || candidate.isAnnotatedWith("jakarta.persistence.MappedSuperclass")) {
                String packageName = candidate.getPackageName();
                int entityIndex = (packageName + ".").indexOf(".entity.");
                if (entityIndex >= 0) {
                    basePackages.add(packageName.substring(0, entityIndex));
                }
            }
        }
        return Set.copyOf(basePackages);
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

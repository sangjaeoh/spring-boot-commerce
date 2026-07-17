package com.commerce.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.architecture.bypass.service.EntityReferencingFixture;
import com.commerce.member.entity.Member;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md의 "빌드가 강제하는 불변식" 중 컨벤션 플러그인(컴파일 시점)이 잡지 못하는 항목을
 * 컴파일된 클래스 그래프에서 검증한다. 이 모듈(module-tests/test-architecture)은 전 모듈을 의존해
 * {@code com.commerce} 전체를 임포트하므로 규칙이 모든 앱·도메인에 활성화된다. 앞 두 규칙은
 * {@code ..facade..}·{@code ..event.listener..}가 생 엔티티({@code @Entity}·{@code @MappedSuperclass})나
 * 도메인 리포지토리에 의존하면 실패한다. 세 번째 규칙은 패키지와 무관하게, 어떤 클래스든 소프트삭제 엔티티
 * 리포지토리의 base finder를 직접 호출하면 실패한다. 네 번째 규칙은 web 컨트롤러가 서로 다른 도메인의
 * service를 2개 이상 직접 의존하면 실패한다 — 크로스 도메인 조율은 facade가 소유한다(architecture.md 앱 모듈 구조).
 * 다섯째·여섯째 규칙은 web 컨트롤러 핸들러에 {@code @Operation}·{@code @ApiResponse}가, request/response 타입·컴포넌트에
 * {@code @Schema}가 없으면 실패한다 — 클라이언트 명세를 코드에 명시한다(architecture.md 표현 계층 OpenAPI 규칙).
 * 일곱째 규칙은 web 컨트롤러 핸들러가 int·Integer {@code @RequestParam}을 직접 선언하면 실패한다 — 페이징
 * 파라미터는 common-web {@code PaginationRequest}로 받는다(architecture.md 앱 모듈 구조 페이징 규칙).
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.commerce");

    // @SpringBootApplication 메인 클래스가 사는 패키지 = 앱 베이스 패키지(앱마다 하나). 앱 하드코딩
    // (com.commerce.api) 대신 여기서 파생해 새 앱이 자동으로 규칙 대상이 된다. CLASSES를 순회하므로 CLASSES 뒤에 둔다.
    private static final Set<String> APP_BASE_PACKAGES = appBasePackages();

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
                .resideInAnyPackage(appSubtreePatterns(".."))
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
    void appBasePackagesAreDetected() {
        // 앱 대상 규칙(리포지토리 직접 접근·컨트롤러 단일 도메인)의 변별력은 이 집합이 채워져 있음에 달렸다 —
        // 비면 검사할 앱이 없어 규칙이 공허해진다. @SpringBootApplication 감지 회귀를 여기서 고정한다.
        assertEquals(Set.of("com.commerce.api", "com.commerce.migration"), APP_BASE_PACKAGES);
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
    void controllerHandlerMethodsDeclareOpenApiDocs() {
        controllerHandlerMethodsDocumented().check(CLASSES);
    }

    @Test
    void requestAndResponseTypesDeclareSchema() {
        requestAndResponseTypesDocumented().check(CLASSES);
    }

    @Test
    void controllerHandlersDoNotDeclareRawPagingParams() {
        controllerHandlersUsePaginationRequest().check(CLASSES);
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
    // 여러 도메인 service를 엮는 facade 자신({app}.facade)은 web 패키지 밖이라 대상이 아니다.
    private static ArchRule controllersDependOnAtMostOneDomainService() {
        return classes()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".web.."))
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

    // web 컨트롤러(@RestController)의 각 핸들러 메서드(@RequestMapping 계열 매핑)가 @Operation과 @ApiResponse(s)를
    // 선언하지 않으면 실패한다 — 클라이언트 명세를 컨트롤러에 명시한다(architecture.md 표현 계층 OpenAPI 규칙). 매칭 대상이
    // 없으면 failOnEmptyShould(기본 on)가 규칙 자체를 실패시켜 공허 통과를 막으므로 별도 감지 테스트를 두지 않는다.
    private static ArchRule controllerHandlerMethodsDocumented() {
        return classes()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".web.."))
                .and()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(declareOpenApiDocsOnHandlers())
                .because("컨트롤러 핸들러는 @Operation·@ApiResponse로 클라이언트 명세를 명시한다 — architecture.md 표현 계층 OpenAPI 규칙");
    }

    private static ArchCondition<JavaClass> declareOpenApiDocsOnHandlers() {
        return new ArchCondition<>("핸들러 메서드마다 @Operation과 @ApiResponse(s)를 선언") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    if (!isHandlerMethod(method)) {
                        continue;
                    }
                    boolean hasOperation = method.isAnnotatedWith("io.swagger.v3.oas.annotations.Operation");
                    boolean hasResponses =
                            method.isAnnotatedWith("io.swagger.v3.oas.annotations.responses.ApiResponses")
                                    || method.isAnnotatedWith("io.swagger.v3.oas.annotations.responses.ApiResponse");
                    if (!hasOperation || !hasResponses) {
                        events.add(SimpleConditionEvent.violated(
                                method, method.getFullName() + " 핸들러에 @Operation·@ApiResponse(s)가 없다"));
                    }
                }
            }
        };
    }

    // @GetMapping·@PostMapping 등은 @RequestMapping을 메타어노테이션으로 달고, @RequestMapping 직접 사용도 매핑이다.
    private static boolean isHandlerMethod(JavaMethod method) {
        String requestMapping = "org.springframework.web.bind.annotation.RequestMapping";
        return method.isAnnotatedWith(requestMapping) || method.isMetaAnnotatedWith(requestMapping);
    }

    // request/response 타입과 그 인스턴스 컴포넌트에 @Schema가 없으면 실패한다 — 필드 의미를 클라이언트 명세에 명시한다
    // (architecture.md 표현 계층 OpenAPI 규칙). 매칭 대상이 없으면 failOnEmptyShould(기본 on)가 규칙 자체를 실패시킨다.
    private static ArchRule requestAndResponseTypesDocumented() {
        return classes()
                .that()
                .resideInAnyPackage(dtoPackagePatterns())
                .should(declareSchemaOnTypeAndComponents())
                .because("request/response의 타입·컴포넌트는 @Schema로 클라이언트 명세를 명시한다 — architecture.md 표현 계층 OpenAPI 규칙");
    }

    private static ArchCondition<JavaClass> declareSchemaOnTypeAndComponents() {
        return new ArchCondition<>("타입과 모든 인스턴스 필드에 @Schema를 선언") {
            @Override
            public void check(JavaClass dto, ConditionEvents events) {
                String schema = "io.swagger.v3.oas.annotations.media.Schema";
                if (!dto.isAnnotatedWith(schema)) {
                    events.add(SimpleConditionEvent.violated(dto, dto.getName() + " 타입에 @Schema가 없다"));
                }
                for (JavaField field : dto.getFields()) {
                    if (field.getModifiers().contains(JavaModifier.STATIC)) {
                        continue;
                    }
                    if (!field.isAnnotatedWith(schema)) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " 필드에 @Schema가 없다"));
                    }
                }
            }
        };
    }

    // web 컨트롤러 핸들러가 int·Integer @RequestParam을 직접 선언하면 실패한다 — 페이징 파라미터는 common-web
    // PaginationRequest로 받는다(architecture.md 앱 모듈 구조 페이징 규칙). -parameters 컴파일이 보장되지 않아
    // 파라미터명("page"·"size") 대신 타입으로 판정한다. 페이징 아닌 정수 쿼리 파라미터가 필요해지면 long 등
    // 다른 타입을 쓰거나 이 규칙의 예외 처리를 결정한다.
    private static ArchRule controllerHandlersUsePaginationRequest() {
        return classes()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".web.."))
                .and()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(notDeclareIntRequestParams())
                .because("페이징 파라미터는 common-web PaginationRequest로 받는다 — architecture.md 앱 모듈 구조 페이징 규칙");
    }

    private static ArchCondition<JavaClass> notDeclareIntRequestParams() {
        return new ArchCondition<>("핸들러에 int·Integer @RequestParam을 선언하지 않음") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    if (!isHandlerMethod(method)) {
                        continue;
                    }
                    for (JavaParameter parameter : method.getParameters()) {
                        String type = parameter.getRawType().getName();
                        boolean intType = type.equals("int") || type.equals("java.lang.Integer");
                        if (intType
                                && parameter.isAnnotatedWith("org.springframework.web.bind.annotation.RequestParam")) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName() + " 가 int·Integer @RequestParam을 직접 받는다 — 페이징은"
                                            + " PaginationRequest로 받는다"));
                        }
                    }
                }
            }
        };
    }

    // 각 앱 베이스의 web request·response 패키지 패턴. 앱 하드코딩 없이 전 앱의 request/response DTO를 대상화한다.
    private static String[] dtoPackagePatterns() {
        return APP_BASE_PACKAGES.stream()
                .flatMap(base -> Stream.of(base + ".web..request..", base + ".web..response.."))
                .toArray(String[]::new);
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

    // 각 앱 베이스 패키지에 접미를 붙인 ArchUnit 패키지 패턴 배열({base}{suffix}). 앱 하드코딩 없이 전 앱을 대상화한다.
    private static String[] appSubtreePatterns(String suffix) {
        return APP_BASE_PACKAGES.stream().map(base -> base + suffix).toArray(String[]::new);
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

    private static Set<String> appBasePackages() {
        Set<String> packages = new HashSet<>();
        for (JavaClass candidate : CLASSES) {
            if (candidate.isAnnotatedWith("org.springframework.boot.autoconfigure.SpringBootApplication")) {
                packages.add(candidate.getPackageName());
            }
        }
        return Set.copyOf(packages);
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

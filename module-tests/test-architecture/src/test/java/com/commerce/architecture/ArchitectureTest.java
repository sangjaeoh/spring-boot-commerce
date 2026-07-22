package com.commerce.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.architecture.bypass.application.EntityReferencingFixture;
import com.commerce.member.domain.Member;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md의 "빌드가 강제하는 불변식" 중 컨벤션 플러그인(컴파일 시점)이 잡지 못하는 항목을
 * 컴파일된 클래스 그래프에서 검증하는 테스트다.
 *
 * <p>이 모듈(module-tests/test-architecture)은 전 모듈을 의존해 {@code com.commerce} 전체를 임포트하므로
 * 규칙이 모든 앱·도메인에 활성화된다.
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

    // 어드민 게이트 어노테이션 FQN — 마커 정렬 규칙과 메서드 레벨 금지 규칙이 공유한다. 어드민 표면은 app-admin이 소유한다.
    private static final String ADMIN_ONLY = "com.commerce.admin.web.auth.Admin";

    // 유효 URL 경로 합성이 읽는 매핑 어노테이션(@RequestMapping과 그 메서드 축약형) FQN.
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping");

    // deletedAt 필드를 가진 @Entity의 FQN 집합. 리포지토리→엔티티 판정은 이 집합만으로 하며 Spring Data 타입 계층
    // 해석에 의존하지 않는다. softDeleteEntityNames가 CLASSES를 순회하므로 반드시 CLASSES 선언 뒤에 둔다.
    private static final Set<String> SOFT_DELETE_ENTITY_NAMES = softDeleteEntityNames();

    // 매핑 클래스(@Entity·@MappedSuperclass)가 사는 {base}.domain(도메인 모듈)·{base}.entity(common-jpa)
    // 패키지에서 파생한 소유 모듈 베이스 패키지 집합 — 도메인 모듈(com.commerce.{domain})과 공통 베이스 엔티티의
    // common-jpa(com.commerce.jpa). 엔티티 접근 면제를 이 접두로 한정한다. entityOwningBasePackages가 CLASSES를
    // 순회하므로 CLASSES 선언 뒤에 둔다.
    private static final Set<String> ENTITY_OWNING_BASE_PACKAGES = entityOwningBasePackages();

    // 도메인 모듈 안에서 @Entity를 정당하게 참조하는 2개 구역(domain 상호참조·exception, application의
    // 유스케이스 서비스·Info.from(Entity)·required 리포지토리). 이벤트 record는 이벤트 모듈
    // (com.commerce.event..) 소유라 도메인 면제 대상이 아니다.
    private static final Set<String> ENTITY_ACCESS_EXEMPT_SUBPACKAGES = Set.of("domain", "application");

    // 도메인 모듈 베이스 패키지 집합 — 매핑 클래스가 domain 구역({base}.domain)에 사는 모듈에서 파생한다.
    // 구역 의존 방향·adapter 비노출·provided 경유 규칙이 대상 범위로 쓴다. CLASSES 순회라 CLASSES 뒤에 둔다.
    private static final Set<String> DOMAIN_BASE_PACKAGES = domainBasePackages();

    // JPA 매핑 클래스(@Entity·@MappedSuperclass) FQN 집합 — provided·info 시그니처 노출 금지 판정에 쓴다.
    private static final Set<String> MAPPED_TYPE_NAMES = mappedTypeNames();

    // 발행 계약 FQN — 발행 주체 제한 규칙이 의존 대상으로 판정한다.
    private static final String MESSAGE_PUBLISHER = "com.commerce.event.publish.MessagePublisher";

    // 이벤트 표식 FQN — 이벤트 record 소유·발행·소비 감지 규칙이 공유한다.
    private static final String DOMAIN_EVENT = "com.commerce.event.event.DomainEvent";

    // 이벤트 record FQN → 발행 도메인 루트: 도메인 application에서 발행 계약과 이벤트 record를 함께 의존하는
    // 클래스에서 파생한다. eventPublisherDomains가 CLASSES를 순회하므로 CLASSES 뒤에 둔다.
    private static final Map<String, Set<String>> EVENT_PUBLISHER_DOMAINS = eventPublisherDomains();

    // 이벤트 record FQN → 소비 도메인 루트: @EventListener 파라미터로 이벤트 record를 받는 도메인 application
    // 클래스에서 파생한다.
    private static final Map<String, Set<String>> EVENT_CONSUMER_DOMAINS = eventConsumerDomains();

    @Test
    @DisplayName("JPA 엔티티는 소유 도메인 모듈 내부에서만 접근된다")
    void jpaEntitiesAreOnlyAccessedWithinTheirOwnDomainModule() {
        jpaEntityAccessConfinedToDomainModules().check(CLASSES);
    }

    @Test
    @DisplayName("매핑 클래스 소유 모듈의 베이스 패키지 감지가 기대 집합과 일치한다")
    void entityOwningBasePackagesAreDetected() {
        // 면제 한정의 변별력은 ENTITY_OWNING_BASE_PACKAGES가 매핑 클래스 소유 모듈만 담고 있음에 달렸다 —
        // 앱 등 다른 모듈에 매핑 클래스가 생기면 그 베이스가 면제 접두로 승격되므로 여기서 실패시킨다.
        assertEquals(
                Set.of(
                        "com.commerce.cart",
                        "com.commerce.coupon",
                        "com.commerce.inquiry",
                        "com.commerce.jpa",
                        "com.commerce.member",
                        "com.commerce.order",
                        "com.commerce.payment",
                        "com.commerce.product",
                        "com.commerce.review",
                        "com.commerce.stock",
                        "com.commerce.wishlist"),
                ENTITY_OWNING_BASE_PACKAGES);
    }

    @Test
    @DisplayName("도메인 밖 application 패키지의 생 엔티티 참조는 면제되지 않는다")
    void entityAccessExemptionDoesNotCoverApplicationPackagesOutsideDomainModules() {
        // 도메인 밖 ...application 패키지의 생 엔티티 참조가 면제 없이 위반으로 잡히는지 고정한다.
        JavaClasses bypassAttempt = new ClassFileImporter().importClasses(EntityReferencingFixture.class, Member.class);

        assertThrows(
                AssertionError.class,
                () -> jpaEntityAccessConfinedToDomainModules().check(bypassAttempt));
    }

    // 면제는 매핑 클래스 소유 모듈의 베이스 패키지 접두로 한정한다 — 전역 패키지명 기준(..application.. 등)이면
    // 앱이 ...application 패키지를 만들어 생 엔티티를 참조해도 면제되는 우회가 생긴다.
    private static ArchRule jpaEntityAccessConfinedToDomainModules() {
        DescribedPredicate<CanBeAnnotated> jpaMapped = annotatedWith("jakarta.persistence.Entity")
                .or(annotatedWith("jakarta.persistence.MappedSuperclass"))
                .as("@Entity 또는 @MappedSuperclass 매핑 클래스");
        DescribedPredicate<JavaClass> entityOwningModuleInternal =
                new DescribedPredicate<>("매핑 클래스 소유 모듈 내부 구역(domain·application)") {
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
    @DisplayName("apps는 도메인 리포지토리에 직접 접근하지 않는다")
    void appsDoNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".."))
                .should()
                .dependOnClassesThat()
                // 리포지토리가 application/required에 포트 계약과 평탄 동거하므로 패키지명 대신 Spring Data
                // Repository 계승으로 판정한다 — 포트 소비(P7 해소 전까지 잔존)는 잡지 않고 리포지토리만 잡는다.
                .areAssignableTo("org.springframework.data.repository.Repository")
                .because("apps는 도메인 리포지토리에 직접 접근하지 않는다 — architecture.md 리포지토리 접근 범위");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("앱 베이스 패키지 감지가 기대 집합과 일치한다")
    void appBasePackagesAreDetected() {
        // 앱 대상 규칙(리포지토리 직접 접근·컨트롤러 단일 도메인)의 변별력은 이 집합이 채워져 있음에 달렸다 —
        // 비면 검사할 앱이 없어 규칙이 공허해진다. @SpringBootApplication 감지 회귀를 여기서 고정한다.
        assertEquals(
                Set.of("com.commerce.admin", "com.commerce.api", "com.commerce.batch", "com.commerce.migration"),
                APP_BASE_PACKAGES);
    }

    @Test
    @DisplayName("소프트삭제 엔티티 리포지토리의 base finder는 직접 호출되지 않는다")
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
    @DisplayName("소프트삭제 엔티티 감지가 기대 집합과 일치한다")
    void softDeleteEntitiesAreDetected() {
        // 규칙의 변별력은 SOFT_DELETE_ENTITY_NAMES가 채워져 있음에 달렸다 — 비면 규칙이 공허하게 통과하고
        // (failOnEmptyShould는 .that() 필터가 없어 못 잡는다) 실제 위반을 놓친다. 감지 회귀를 여기서 실패시킨다.
        assertEquals(
                Set.of(
                        "com.commerce.member.domain.Member",
                        "com.commerce.product.domain.Product",
                        "com.commerce.product.domain.Category"),
                SOFT_DELETE_ENTITY_NAMES);
    }

    @Test
    @DisplayName("컨트롤러는 최대 한 도메인의 service에만 직접 의존한다")
    void controllersDependOnAtMostOneDomainServiceDirectly() {
        controllersDependOnAtMostOneDomainService().check(CLASSES);
    }

    @Test
    @DisplayName("컨트롤러 핸들러는 @Operation·@ApiResponse(s)를 선언한다")
    void controllerHandlerMethodsDeclareOpenApiDocs() {
        controllerHandlerMethodsDocumented().check(CLASSES);
    }

    @Test
    @DisplayName("request·response 타입과 인스턴스 컴포넌트는 @Schema를 선언한다")
    void requestAndResponseTypesDeclareSchema() {
        requestAndResponseTypesDocumented().check(CLASSES);
    }

    @Test
    @DisplayName("핸들러의 @RequestParam·@PathVariable은 description 있는 @Parameter를 선언한다")
    void controllerHandlerParametersDeclareParameterDocs() {
        controllerHandlerParametersDocumented().check(CLASSES);
    }

    @Test
    @DisplayName("핸들러는 페이징을 int·Integer @RequestParam으로 직접 받지 않는다")
    void controllerHandlersDoNotDeclareRawPagingParams() {
        controllerHandlersUsePaginationRequest().check(CLASSES);
    }

    @Test
    @DisplayName("어드민 표면 마커(패키지·네이밍·@Admin·URL)는 함께만 나타난다")
    void adminWebSurfaceMarkersAppearOnlyTogether() {
        adminSurfaceMarkersAligned().check(CLASSES);
    }

    @Test
    @DisplayName("도메인 베이스 패키지 감지가 기대 집합과 일치한다")
    void domainBasePackagesAreDetected() {
        // 구역 규칙들의 변별력은 이 집합이 도메인 모듈 전부를 담고 있음에 달렸다 — 감지 회귀를 여기서 고정한다.
        assertEquals(
                Set.of(
                        "com.commerce.cart",
                        "com.commerce.coupon",
                        "com.commerce.inquiry",
                        "com.commerce.member",
                        "com.commerce.order",
                        "com.commerce.payment",
                        "com.commerce.product",
                        "com.commerce.review",
                        "com.commerce.stock",
                        "com.commerce.wishlist"),
                DOMAIN_BASE_PACKAGES);
    }

    @Test
    @DisplayName("도메인 구역 의존 방향 — domain은 타 구역에, application은 adapter에 의존하지 않는다")
    void domainZoneDependencyDirectionsAreEnforced() {
        noClasses()
                .that()
                .resideInAnyPackage(domainZonePatterns(".domain.."))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(concat(domainZonePatterns(".application.."), domainZonePatterns(".adapter..")))
                .because("domain 구역은 다른 구역에 의존하지 않는다 — architecture.md 도메인 모듈 구조")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAnyPackage(domainZonePatterns(".application.."))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(domainZonePatterns(".adapter.."))
                .because("application 구역은 adapter 구역에 의존하지 않는다 — architecture.md 도메인 모듈 구조")
                .check(CLASSES);
    }

    @Test
    @DisplayName("adapter 구역은 소유 모듈 밖에서 접근되지 않는다")
    void adapterZonesAreNotAccessedOutsideOwningModule() {
        classes()
                .should(notDependOnForeignAdapterZones())
                .because("required 구현(adapter)은 모듈 밖에 노출하지 않는다 — architecture.md 도메인 모듈 구조")
                .check(CLASSES);
    }

    @Test
    @DisplayName("infra 타입은 모듈 밖에서 임포트되지 않는다")
    void infraTypesAreNotImportedOutsideInfraModules() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.commerce.infra..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.commerce.infra..")
                .because("infra 소비는 common 계약 경유만 허용한다 — architecture.md 빌드가 강제하는 불변식")
                .check(CLASSES);
    }

    @Test
    @DisplayName("이벤트 계약을 구현하는 record는 이벤트 모듈만 소유한다")
    void domainEventImplementationsResideInEventModules() {
        classes()
                .that()
                .areAssignableTo(DOMAIN_EVENT)
                .and()
                .areNotInterfaces()
                .should()
                .resideInAPackage("com.commerce.event..")
                .because("발행 이벤트 record는 이벤트 모듈(event-{name})이 전수 소유한다 — architecture.md 이벤트 모듈 구조")
                .check(CLASSES);
    }

    @Test
    @DisplayName("발행 계약 사용은 도메인 application(과 전달 구현 infra·계약 소유 common-event)만 허용된다")
    void messagePublisherIsUsedOnlyByDomainApplication() {
        DescribedPredicate<JavaClass> allowedPublisherOrigin =
                new DescribedPredicate<>("도메인 application 구역·infra 전달 구현·common-event 계약 패키지") {
                    @Override
                    public boolean test(JavaClass clazz) {
                        String packageName = clazz.getPackageName();
                        return domainServiceRoot(packageName).isPresent()
                                || packageName.startsWith("com.commerce.infra.")
                                || packageName.startsWith("com.commerce.event.");
                    }
                };
        noClasses()
                .that(not(allowedPublisherOrigin))
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MESSAGE_PUBLISHER)
                .because("조합 계층(파사드·앱)은 이벤트를 발행하지 않는다 — 발행은 도메인 쓰기 서비스가 상태 전이와 같은"
                        + " 트랜잭션에서 수행한다. architecture.md 빌드가 강제하는 불변식")
                .check(CLASSES);
    }

    @Test
    @DisplayName("이벤트 발행·소비 도메인 감지가 기대 집합과 일치한다")
    void eventPublisherAndConsumerDomainsAreDetected() {
        // 소비 커버리지 규칙의 변별력은 두 맵이 실제 발행·소비를 담고 있음에 달렸다 — 감지 회귀를 여기서 고정한다.
        assertEquals(Map.of("com.commerce.event.order.OrderPaid", Set.of("order")), EVENT_PUBLISHER_DOMAINS);
        assertEquals(Map.of("com.commerce.event.order.OrderPaid", Set.of("cart")), EVENT_CONSUMER_DOMAINS);
    }

    @Test
    @DisplayName("실행 앱은 임베드한 발행 도메인의 이벤트 소비 도메인을 함께 임베드한다")
    void appsEmbedConsumerDomainsOfEmbeddedPublisherDomains() {
        Map<String, Set<String>> embeddedDomainsByApp = appDomainEmbeddings();
        assertEquals(Set.of("app-admin", "app-api", "app-batch", "app-migration"), embeddedDomainsByApp.keySet());

        List<String> violations = new ArrayList<>();
        embeddedDomainsByApp.forEach((app, embedded) -> EVENT_PUBLISHER_DOMAINS.forEach((event, publishers) -> {
            if (publishers.stream().anyMatch(embedded::contains)) {
                for (String consumer : EVENT_CONSUMER_DOMAINS.getOrDefault(event, Set.of())) {
                    if (!embedded.contains(consumer)) {
                        violations.add(app + " 가 " + event + " 발행 도메인을 임베드하면서 소비 도메인 " + consumer + " 를 임베드하지 않는다");
                    }
                }
            }
        }));
        assertEquals(List.of(), violations);
    }

    @Test
    @DisplayName("provided·info의 공개 시그니처는 JPA 매핑 타입을 노출하지 않는다")
    void providedAndInfoSignaturesDoNotExposeJpaMappedTypes() {
        classes()
                .that()
                .resideInAnyPackage(concat(
                        domainZonePatterns(".application.provided.."), domainZonePatterns(".application.info..")))
                .should(notExposeMappedTypesInBoundarySignatures())
                .because("JPA 매핑 클래스는 모듈 밖 시그니처에 노출하지 않는다 — 경계는 info record로 변환한다." + " architecture.md 경계 원칙")
                .check(CLASSES);
    }

    @Test
    @DisplayName("도메인 구현 서비스는 모듈 밖에서 접근되지 않는다")
    void domainImplementationServicesAreNotAccessedOutsideModule() {
        classes()
                .should(notDependOnForeignApplicationImplementations())
                .because("모듈 밖 호출은 provided 인터페이스를 경유한다 — 구현 서비스는 모듈 밖에 노출하지 않는다." + " architecture.md 경계 원칙")
                .check(CLASSES);
    }

    @Test
    @DisplayName("도메인 모듈은 앱 표면 common(common-web·common-auth)에 의존하지 않는다")
    void domainModulesDoNotDependOnAppSurfaceCommons() {
        noClasses()
                .that()
                .resideInAnyPackage(domainSubtreePatterns())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.commerce.web..", "com.commerce.auth..")
                .because("도메인은 앱 표면 관심사 common에 의존하지 않는다 — architecture.md 빌드가 강제하는 불변식")
                .check(CLASSES);
    }

    @Test
    @DisplayName("도메인 application 패키지 판정이 대표 사례를 정확히 가른다")
    void domainServiceRootParsingIsCorrect() {
        // 규칙의 변별력은 도메인 application 패키지 판정의 정확성에 달렸다 — facade·info·required·common을
        // 유스케이스 서비스로 오인하면 규칙이 공허해지거나(도메인 0개로 항상 통과) 오탐한다. 파싱 회귀를 여기서 고정한다.
        assertEquals(Optional.of("order"), domainServiceRoot("com.commerce.order.application"));
        assertEquals(Optional.of("payment"), domainServiceRoot("com.commerce.payment.application.support"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.api.facade"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.order.application.info"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.order.application.required"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.auth.token"));
        assertEquals(Optional.empty(), domainServiceRoot("com.commerce.web.auth"));
    }

    // web 컨트롤러가 서로 다른 도메인의 application(유스케이스 서비스)에 2개 이상 직접 의존하면 실패한다. 컨트롤러는
    // 단일 도메인 서비스(또는 facade)에만 얇게 위임하고, 크로스 도메인 조율은 facade가 소유한다 — architecture.md 앱
    // 모듈 구조. 여러 도메인 서비스를 엮는 facade 자신({app}.facade)은 web 패키지 밖이라 대상이 아니다.
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

    // web 컨트롤러 핸들러의 @RequestParam·@PathVariable 파라미터에 description이 비어 있지 않은 @Parameter가 없으면
    // 실패한다 — 이름·타입·필수 여부는 springdoc이 자동 생성하지만 의미는 코드에 명시한다(architecture.md 표현 계층
    // OpenAPI 규칙). @ParameterObject DTO 파라미터는 필드 @Schema 규칙이 커버하므로 대상이 아니다.
    private static ArchRule controllerHandlerParametersDocumented() {
        return classes()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".web.."))
                .and()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(declareParameterDocsOnHandlerParameters())
                .because("핸들러의 @RequestParam·@PathVariable은 @Parameter(description)로 클라이언트 명세를 명시한다"
                        + " — architecture.md 표현 계층 OpenAPI 규칙");
    }

    private static ArchCondition<JavaClass> declareParameterDocsOnHandlerParameters() {
        return new ArchCondition<>("@RequestParam·@PathVariable 파라미터마다 description 있는 @Parameter를 선언") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    if (!isHandlerMethod(method)) {
                        continue;
                    }
                    for (JavaParameter parameter : method.getParameters()) {
                        boolean bindsInlineValue = parameter.isAnnotatedWith(
                                        "org.springframework.web.bind.annotation.RequestParam")
                                || parameter.isAnnotatedWith("org.springframework.web.bind.annotation.PathVariable");
                        if (bindsInlineValue && !hasParameterDescription(parameter)) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName() + " 의 " + parameter.getIndex()
                                            + "번 파라미터에 description 있는 @Parameter가 없다"));
                        }
                    }
                }
            }
        };
    }

    private static boolean hasParameterDescription(JavaParameter parameter) {
        return parameter.getAnnotations().stream()
                .filter(annotation ->
                        annotation.getRawType().getName().equals("io.swagger.v3.oas.annotations.Parameter"))
                .findFirst()
                .flatMap(annotation -> annotation.get("description"))
                .map(Object::toString)
                .filter(description -> !description.isBlank())
                .isPresent();
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

    // 어드민 표면의 네 마커(admin 패키지·*AdminController 네이밍·클래스 @Admin·admin URL 네임스페이스)가
    // 함께만 나타나면 통과한다 — 새 핸들러가 어노테이션 누락으로 조용히 일반 노출되는 사고를 클래스 레벨 기본
    // 잠금으로 차단한다(architecture.md 앱 모듈 구조 어드민 표면 규칙). URL 마커는 클래스·핸들러 매핑을 합성한
    // 유효 경로로 판정해 path 별칭·다중 경로·메서드 레벨 admin 경로도 잡는다.
    private static ArchRule adminSurfaceMarkersAligned() {
        return classes()
                .that()
                .resideInAnyPackage(appSubtreePatterns(".web.."))
                .and()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(alignAdminSurfaceMarkers())
                .because("어드민 표면은 admin 패키지의 *AdminController가 클래스 레벨 @Admin와 /api/v{n}/admin/"
                        + " URL 프리픽스로 소유한다 — architecture.md 앱 모듈 구조 어드민 표면 규칙");
    }

    private static ArchCondition<JavaClass> alignAdminSurfaceMarkers() {
        return new ArchCondition<>(
                "어드민 마커(admin 패키지·*AdminController·클래스 @Admin·admin URL 네임스페이스)를 전부 갖추거나 전부 갖추지 않음") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                boolean inAdminPackage = hasAdminSegmentAfterWeb(controller.getPackageName());
                boolean adminNamed = controller.getSimpleName().endsWith("AdminController");
                boolean classAdmin = controller.isAnnotatedWith(ADMIN_ONLY);
                Set<String> paths = effectiveMappingPaths(controller);
                boolean anyAdminPath = paths.stream().anyMatch(ArchitectureTest::isAdminPath);
                boolean allAdminPaths = !paths.isEmpty() && paths.stream().allMatch(ArchitectureTest::isAdminPath);
                boolean anyMarker = inAdminPackage || adminNamed || classAdmin || anyAdminPath;
                boolean allMarkers = inAdminPackage && adminNamed && classAdmin && anyAdminPath;
                if (anyMarker && !allMarkers) {
                    events.add(SimpleConditionEvent.violated(
                            controller,
                            controller.getName() + " 의 어드민 마커가 부분적이다 — admin 패키지=" + inAdminPackage
                                    + ", *AdminController 네이밍=" + adminNamed + ", 클래스 @Admin=" + classAdmin
                                    + ", /api/v{n}/admin/ 경로=" + anyAdminPath));
                }
                if (anyAdminPath && !allAdminPaths) {
                    events.add(SimpleConditionEvent.violated(
                            controller,
                            controller.getName() + " 가 admin·비-admin URL을 혼재 매핑한다: " + new TreeSet<>(paths)
                                    + " — 어드민 표면은 /api/v{n}/admin/ 네임스페이스로만 매핑한다"));
                }
            }
        };
    }

    private static boolean isAdminPath(String path) {
        return path.matches("/api/v\\d+/admin(/.*)?");
    }

    // web 세그먼트 뒤에 admin 세그먼트가 있으면 참이다. 베이스 패키지에 admin이 들어가는 앱(app-admin)의 일반
    // 패키지가 어드민 표면으로 오인되지 않게 web 뒤로 한정한다.
    private static boolean hasAdminSegmentAfterWeb(String packageName) {
        List<String> segments = List.of(packageName.split("\\."));
        int webIndex = segments.indexOf("web");
        return webIndex >= 0 && segments.subList(webIndex + 1, segments.size()).contains("admin");
    }

    // 컨트롤러가 실제로 서빙하는 유효 URL 경로 집합 — 클래스 매핑 경로 × 핸들러 매핑 경로의 합성. 클래스 레벨
    // @RequestMapping만 읽으면 클래스 매핑 없는 절대 메서드 경로나 상대 메서드 경로로 합성되는 admin URL을 놓친다.
    private static Set<String> effectiveMappingPaths(JavaClass controller) {
        Set<String> basePaths = declaredMappingPaths(controller.getAnnotations());
        if (basePaths.isEmpty()) {
            basePaths = Set.of("");
        }
        Set<String> effective = new HashSet<>();
        for (JavaMethod method : controller.getMethods()) {
            if (!isHandlerMethod(method)) {
                continue;
            }
            Set<String> methodPaths = declaredMappingPaths(method.getAnnotations());
            if (methodPaths.isEmpty()) {
                methodPaths = Set.of("");
            }
            for (String base : basePaths) {
                for (String sub : methodPaths) {
                    effective.add(joinUrlPaths(base, sub));
                }
            }
        }
        return effective;
    }

    // 매핑 어노테이션(@RequestMapping·메서드 축약형)에 선언된 경로를 value·path 별칭 양쪽에서 모은다.
    private static Set<String> declaredMappingPaths(Set<? extends JavaAnnotation<?>> annotations) {
        Set<String> paths = new HashSet<>();
        for (JavaAnnotation<?> annotation : annotations) {
            if (!MAPPING_ANNOTATIONS.contains(annotation.getRawType().getName())) {
                continue;
            }
            for (String attribute : List.of("value", "path")) {
                annotation.get(attribute).ifPresent(declared -> {
                    for (Object path : (Object[]) declared) {
                        paths.add(path.toString());
                    }
                });
            }
        }
        return paths;
    }

    private static String joinUrlPaths(String base, String sub) {
        String joined = (base + "/" + sub).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    // 각 앱 베이스의 web request·response 패키지 패턴. 앱 하드코딩 없이 전 앱의 request/response DTO를 대상화한다.
    private static String[] dtoPackagePatterns() {
        return APP_BASE_PACKAGES.stream()
                .flatMap(base -> Stream.of(base + ".web..request..", base + ".web..response.."))
                .toArray(String[]::new);
    }

    // com.commerce.{root}.application[.*] 패키지(경계 모델 info·요구 계약 required 하위 제외)면 도메인 루트를
    // 반환한다. 도메인 모듈만 application 구역을 두므로(facade·domain·common엔 없다) 이 판정으로 도메인
    // 유스케이스 서비스 의존을 가른다.
    private static Optional<String> domainServiceRoot(String packageName) {
        String prefix = "com.commerce.";
        if (!packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] segments = packageName.substring(prefix.length()).split("\\.");
        if (segments.length >= 2
                && segments[1].equals("application")
                && (segments.length == 2 || !Set.of("info", "required").contains(segments[2]))) {
            return Optional.of(segments[0]);
        }
        return Optional.empty();
    }

    // 각 앱 베이스 패키지에 접미를 붙인 ArchUnit 패키지 패턴 배열({base}{suffix}). 앱 하드코딩 없이 전 앱을 대상화한다.
    private static String[] appSubtreePatterns(String suffix) {
        return APP_BASE_PACKAGES.stream().map(base -> base + suffix).toArray(String[]::new);
    }

    // 클래스가 매핑 클래스 소유 모듈의 면제 구역({base}.{domain|application} 이하)에 있으면 참이다.
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
                // 도메인 모듈은 domain 구역, common-jpa는 구역 규칙 비적용이라 entity 패키지가 마커다.
                int zoneIndex = (packageName + ".").indexOf(".domain.");
                if (zoneIndex < 0) {
                    zoneIndex = (packageName + ".").indexOf(".entity.");
                }
                if (zoneIndex >= 0) {
                    basePackages.add(packageName.substring(0, zoneIndex));
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

    // 각 도메인 베이스에 구역 접미를 붙인 ArchUnit 패키지 패턴 배열({base}{zoneSuffix}).
    private static String[] domainZonePatterns(String zoneSuffix) {
        return DOMAIN_BASE_PACKAGES.stream().map(base -> base + zoneSuffix).toArray(String[]::new);
    }

    // 각 도메인 베이스의 전체 서브트리 패턴({base}..).
    private static String[] domainSubtreePatterns() {
        return DOMAIN_BASE_PACKAGES.stream().map(base -> base + "..").toArray(String[]::new);
    }

    private static String[] concat(String[] first, String[] second) {
        return Stream.concat(Arrays.stream(first), Arrays.stream(second)).toArray(String[]::new);
    }

    private static Set<String> domainBasePackages() {
        Set<String> basePackages = new HashSet<>();
        for (JavaClass candidate : CLASSES) {
            if (candidate.isAnnotatedWith("jakarta.persistence.Entity")
                    || candidate.isAnnotatedWith("jakarta.persistence.MappedSuperclass")) {
                String packageName = candidate.getPackageName();
                int zoneIndex = (packageName + ".").indexOf(".domain.");
                if (zoneIndex >= 0) {
                    basePackages.add(packageName.substring(0, zoneIndex));
                }
            }
        }
        return Set.copyOf(basePackages);
    }

    private static Set<String> mappedTypeNames() {
        Set<String> names = new HashSet<>();
        for (JavaClass candidate : CLASSES) {
            if (candidate.isAnnotatedWith("jakarta.persistence.Entity")
                    || candidate.isAnnotatedWith("jakarta.persistence.MappedSuperclass")) {
                names.add(candidate.getName());
            }
        }
        return Set.copyOf(names);
    }

    // 클래스가 의존하는 이벤트 record(DomainEvent 구현, 비인터페이스) FQN 집합.
    private static Set<String> dependedEventRecords(JavaClass clazz) {
        Set<String> events = new HashSet<>();
        for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
            JavaClass target = dependency.getTargetClass();
            if (!target.isInterface() && target.isAssignableTo(DOMAIN_EVENT)) {
                events.add(target.getName());
            }
        }
        return events;
    }

    private static Map<String, Set<String>> eventPublisherDomains() {
        Map<String, Set<String>> publishers = new HashMap<>();
        for (JavaClass candidate : CLASSES) {
            Optional<String> domain = domainServiceRoot(candidate.getPackageName());
            if (domain.isEmpty()) {
                continue;
            }
            boolean usesPublisher = candidate.getDirectDependenciesFromSelf().stream()
                    .anyMatch(
                            dependency -> dependency.getTargetClass().getName().equals(MESSAGE_PUBLISHER));
            if (!usesPublisher) {
                continue;
            }
            for (String event : dependedEventRecords(candidate)) {
                publishers.computeIfAbsent(event, key -> new HashSet<>()).add(domain.get());
            }
        }
        return Map.copyOf(publishers);
    }

    private static Map<String, Set<String>> eventConsumerDomains() {
        Map<String, Set<String>> consumers = new HashMap<>();
        for (JavaClass candidate : CLASSES) {
            Optional<String> domain = domainServiceRoot(candidate.getPackageName());
            if (domain.isEmpty()) {
                continue;
            }
            for (JavaMethod method : candidate.getMethods()) {
                if (!method.isAnnotatedWith("org.springframework.context.event.EventListener")) {
                    continue;
                }
                for (JavaClass parameterType : method.getRawParameterTypes()) {
                    if (!parameterType.isInterface() && parameterType.isAssignableTo(DOMAIN_EVENT)) {
                        consumers
                                .computeIfAbsent(parameterType.getName(), key -> new HashSet<>())
                                .add(domain.get());
                    }
                }
            }
        }
        return Map.copyOf(consumers);
    }

    // 앱 이름 → 임베드한 도메인 루트("cart" 등) 집합. 빌드가 산출한 리소스(앱 main 런타임 구성의
    // domain-{name} 의존 목록)를 읽는다.
    private static Map<String, Set<String>> appDomainEmbeddings() {
        InputStream resource = ArchitectureTest.class.getResourceAsStream("/app-domain-embeddings.properties");
        if (resource == null) {
            throw new IllegalStateException("app-domain-embeddings.properties 리소스가 없다 — 빌드 산출 태스크를 확인한다");
        }
        Map<String, Set<String>> embeddings = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                Set<String> domains = new HashSet<>();
                for (String moduleName : parts[1].split(",")) {
                    if (!moduleName.isBlank()) {
                        domains.add(moduleName.replaceFirst("^domain-", ""));
                    }
                }
                embeddings.put(parts[0], domains);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return embeddings;
    }

    // 타 도메인 모듈의 adapter 구역 의존을 위반으로 잡는다 — 소유 모듈 내부({base}.. 이하)만 접근할 수 있다.
    private static ArchCondition<JavaClass> notDependOnForeignAdapterZones() {
        return new ArchCondition<>("소유 모듈 밖 adapter 구역에 의존하지 않음") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName() + ".";
                    for (String base : DOMAIN_BASE_PACKAGES) {
                        if (targetPackage.startsWith(base + ".adapter.")
                                && !(origin.getPackageName() + ".").startsWith(base + ".")) {
                            events.add(SimpleConditionEvent.violated(
                                    origin,
                                    origin.getName() + " 가 모듈 밖 adapter 구역에 의존한다: "
                                            + dependency.getTargetClass().getName()));
                        }
                    }
                }
            }
        };
    }

    // 타 도메인 모듈 application 평탄 구역(구현 서비스·소비자)의 의존을 위반으로 잡는다 — provided·info·required
    // 서브 패키지는 대상이 아니다.
    private static ArchCondition<JavaClass> notDependOnForeignApplicationImplementations() {
        return new ArchCondition<>("소유 모듈 밖 application 구현에 의존하지 않음") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    for (String base : DOMAIN_BASE_PACKAGES) {
                        if (targetPackage.equals(base + ".application")
                                && !(origin.getPackageName() + ".").startsWith(base + ".")) {
                            events.add(SimpleConditionEvent.violated(
                                    origin,
                                    origin.getName() + " 가 모듈 밖 도메인 구현 서비스에 의존한다: "
                                            + dependency.getTargetClass().getName()));
                        }
                    }
                }
            }
        };
    }

    // provided 인터페이스는 메서드 시그니처(반환·파라미터·1단계 타입 인자), info record는 인스턴스 필드 타입을
    // 검사한다. info의 정적 변환 팩토리(Info.from(Entity))는 모듈 내부 진입 변환이라 검사 대상이 아니다.
    private static ArchCondition<JavaClass> notExposeMappedTypesInBoundarySignatures() {
        return new ArchCondition<>("공개 시그니처에 JPA 매핑 타입을 노출하지 않음") {
            @Override
            public void check(JavaClass boundaryClass, ConditionEvents events) {
                boolean info = boundaryClass.getPackageName().contains(".application.info");
                if (info) {
                    for (JavaField field : boundaryClass.getFields()) {
                        if (field.getModifiers().contains(JavaModifier.STATIC)) {
                            continue;
                        }
                        reportMappedType(events, field.getFullName() + " 필드", field.getType());
                    }
                    return;
                }
                for (JavaMethod method : boundaryClass.getMethods()) {
                    reportMappedType(events, method.getFullName() + " 반환", method.getReturnType());
                    for (JavaType parameterType : method.getParameterTypes()) {
                        reportMappedType(events, method.getFullName() + " 파라미터", parameterType);
                    }
                }
            }

            private void reportMappedType(ConditionEvents events, String location, JavaType type) {
                for (String typeName : erasedTypeNames(type)) {
                    if (MAPPED_TYPE_NAMES.contains(typeName)) {
                        events.add(SimpleConditionEvent.violated(
                                type.toErasure(), location + " 시그니처가 JPA 매핑 타입을 노출한다: " + typeName));
                    }
                }
            }
        };
    }

    // 타입의 소거 이름과 1단계 제네릭 타입 인자 이름들 — List<Entity>·Page<Entity> 노출까지 잡는다.
    private static List<String> erasedTypeNames(JavaType type) {
        List<String> names = new ArrayList<>();
        names.add(type.toErasure().getName());
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                names.add(argument.toErasure().getName());
            }
        }
        return names;
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

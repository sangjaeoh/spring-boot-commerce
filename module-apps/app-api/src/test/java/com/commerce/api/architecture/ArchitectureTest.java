package com.commerce.api.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md의 "빌드가 강제하는 불변식" 중 컨벤션 플러그인(컴파일 시점)이 잡지 못하는 항목을
 * 컴파일된 클래스 그래프에서 검증한다. app-api가 도메인을 의존하는 P2부터 두 규칙은 비공허하게 활성화돼,
 * {@code ..facade..}·{@code ..event.listener..}가 생 엔티티({@code @Entity}·{@code @MappedSuperclass})나
 * 도메인 리포지토리에 의존하면 실패한다.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.commerce");

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
}

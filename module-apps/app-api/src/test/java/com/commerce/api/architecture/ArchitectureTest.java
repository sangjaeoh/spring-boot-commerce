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
 * 컴파일된 클래스 그래프에서 검증한다. 지금은 app-api가 도메인 모듈을 의존하지 않아 두 규칙 모두
 * 공허하게(스캔 대상 0건) 통과한다 — 파사드·컨트롤러가 도메인을 의존하면 실제로 위반을 잡기 시작한다.
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
                .resideOutsideOfPackages(
                        "..entity..",
                        "..info..",
                        "..service..",
                        "..repository..",
                        "..event..",
                        "..exception..",
                        "..port..")
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
                .resideInAPackage("..repository..")
                .because("apps는 리포지토리에 직접 접근하지 않는다 — architecture.md 리포지토리 접근 범위");

        rule.check(CLASSES);
    }
}

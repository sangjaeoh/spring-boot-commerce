# query-order QueryDSL 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `DefaultOrderSearchReader`의 EntityManager+JPQL 문자열 조회를 QueryDSL로 전환한다 — architecture.md "조회는 QueryDSL 기본" 규칙 준수.

**Architecture:** QueryDSL(OpenFeign fork 7.5)을 레포 최초 도입한다. Q타입은 엔티티 소유 모듈에서 apt로 생성해 jar에 포함한다(common-jpa의 `QBaseTimeEntity` 포함 — apt는 바이너리 상위타입의 Q타입을 생성하지 않으므로 필수). Reader는 DTO 생성자 프로젝션으로 재작성하고 기존 영속성 테스트를 무수정 통과시켜 행동 보존을 증명한다.

**Tech Stack:** Java 25, Spring Boot 4.1.0(Hibernate 7), QueryDSL `io.github.openfeign.querydsl:7.5`, Gradle 컨벤션 플러그인(build-logic), Testcontainers PostgreSQL.

**스펙:** `docs/superpowers/specs/2026-07-23-query-order-querydsl-design.md`

## Global Constraints

- QueryDSL 좌표는 `io.github.openfeign.querydsl`, 버전 `7.5`. 원본 `com.querydsl` 금지.
- `querydsl-apt`는 classifier `jakarta` 필수. TOML이 classifier를 지원하지 않으므로 카탈로그가 아닌 빌드 스크립트에서 문자열 좌표로 선언한다.
- apt 프로세서 경로 의존(querydsl-codegen·jakarta.persistence-api·jakarta.inject-api)은 pom 전이로 해석된다 — 명시 추가 금지.
- Lombok·H2 의존 금지(java-common이 강제), NullAway는 error 레벨, 포맷은 palantir-java-format(커밋 전 `spotlessApply`).
- `OrderSearchReader`·`OrderSearchInfo` 시그니처 변경 금지. `OrderSearchPersistenceTest` 수정 금지.
- 테스트 실행에 Docker 데몬 필요(Testcontainers PostgreSQL).
- 커밋 메시지는 한국어 conventional commit(`build:`·`refactor:` 등)을 따른다.

---

### Task 1: QueryDSL 빌드 도입 + Q타입 생성

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build-logic/src/main/kotlin/convention.java-base.gradle.kts`
- Modify: `build-logic/src/main/kotlin/convention.domain-module.gradle.kts`
- Modify: `build-logic/src/main/kotlin/convention.query-module.gradle.kts`
- Modify: `module-common/common-jpa/build.gradle.kts`

**Interfaces:**
- Consumes: 없음(첫 태스크).
- Produces:
  - 카탈로그: `[versions] querydsl = "7.5"`, `[libraries] querydsl-core`, `querydsl-jpa`.
  - Q타입(컴파일 산출물, jar 포함): `com.commerce.common.jpa.entity.QBaseTimeEntity`, `com.commerce.domain.order.domain.QOrder`(정적 인스턴스 `QOrder.order`), `com.commerce.domain.member.domain.QMember`(정적 인스턴스 `QMember.member`).
  - query 모듈 컴파일 클래스패스에 `com.querydsl.jpa.impl.JPAQueryFactory` 등 querydsl-jpa 타입.

- [ ] **Step 1: 버전 카탈로그에 querydsl 추가**

`gradle/libs.versions.toml`의 `[versions]`에서 `shedlock = "7.7.0"` 줄 바로 아래에 추가:

```toml
# 조회 기본 구현 기술(OpenFeign fork — 원본 com.querydsl은 릴리스 정체). Spring Boot BOM 비관리라 버전을 명시한다
querydsl = "7.5"
```

`[libraries]`에서 `spring-boot-starter-data-jpa` 줄 바로 아래에 추가:

```toml
# convention.domain-module·convention.query-module — QueryDSL(OpenFeign fork). querydsl-apt는 classifier(jakarta)가
# 필요한데 TOML이 classifier를 지원하지 않아 소비 빌드 스크립트에서 문자열 좌표로 선언한다
querydsl-core = { module = "io.github.openfeign.querydsl:querydsl-core", version.ref = "querydsl" }
querydsl-jpa = { module = "io.github.openfeign.querydsl:querydsl-jpa", version.ref = "querydsl" }
```

- [ ] **Step 2: ErrorProne 게이트에서 apt 생성 소스 제외**

`build-logic/src/main/kotlin/convention.java-base.gradle.kts`의 `tasks.withType<JavaCompile>().configureEach { options.errorprone { ... } }` 블록에서 `error("NullAway")` 줄 바로 위에 추가:

```kotlin
        // apt 생성 소스(QueryDSL Q타입)는 우리 코드가 아니므로 게이트 대상에서 제외한다
        excludedPaths.set(".*/build/generated/sources/annotationProcessor/.*")
```

- [ ] **Step 3: 도메인 컨벤션에 apt + querydsl-core 추가**

`build-logic/src/main/kotlin/convention.domain-module.gradle.kts`의 `dependencies` 블록에서 `"implementation"(libsCatalog.findLibrary("spring-boot-starter-data-jpa").get())` 줄 아래에 추가:

```kotlin
    // Q타입 생성(QueryDSL apt). 생성 코드가 참조하는 querydsl-core를 컴파일 클래스패스에 둔다.
    // apt classifier(jakarta)는 TOML 미지원이라 문자열 좌표로 선언한다. 프로세서 경로 의존은 pom 전이로 해석된다.
    "implementation"(libsCatalog.findLibrary("querydsl-core").get())
    "annotationProcessor"(
        "io.github.openfeign.querydsl:querydsl-apt:${
            libsCatalog.findVersion("querydsl").get().requiredVersion
        }:jakarta",
    )
```

- [ ] **Step 4: query 컨벤션에 querydsl-jpa 추가**

`build-logic/src/main/kotlin/convention.query-module.gradle.kts`의 `dependencies` 블록에서 `"implementation"(libsCatalog.findLibrary("spring-boot-starter-data-jpa").get())` 줄 아래에 추가:

```kotlin
    // 조회 기본 구현 기술 — JPAQueryFactory·Q타입 사용(architecture.md query 모듈 구조)
    "implementation"(libsCatalog.findLibrary("querydsl-jpa").get())
```

- [ ] **Step 5: common-jpa에 apt + querydsl-core 추가**

`module-common/common-jpa/build.gradle.kts`의 `dependencies` 블록에서 `implementation(libs.spring.boot.starter.data.jpa)` 줄 아래에 추가:

```kotlin
    // BaseTimeEntity의 Q타입(QBaseTimeEntity)을 생성해 jar에 포함한다 — apt는 바이너리(의존성 jar) 상위타입의
    // Q타입을 생성하지 않으므로, 도메인 엔티티 Q타입이 참조하는 상위 Q타입은 소유 모듈이 배포해야 한다.
    implementation(libs.querydsl.core)
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
```

- [ ] **Step 6: 컴파일로 Q타입 생성 검증**

Run:

```bash
./gradlew :module-common:common-jpa:compileJava :module-domains:domain-order:compileJava :module-domains:domain-member:compileJava :module-query:query-order:compileJava
```

Expected: `BUILD SUCCESSFUL`

Run:

```bash
ls module-common/common-jpa/build/generated/sources/annotationProcessor/java/main/com/commerce/common/jpa/entity/
ls module-domains/domain-order/build/generated/sources/annotationProcessor/java/main/com/commerce/domain/order/domain/
ls module-domains/domain-member/build/generated/sources/annotationProcessor/java/main/com/commerce/domain/member/domain/
```

Expected: 각각 `QBaseTimeEntity.java` 포함, `QOrder.java` 포함, `QMember.java` 포함.

- [ ] **Step 7: 전체 컴파일·게이트 검증 (테스트 제외)**

Run:

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL` — 전 도메인 모듈 apt 적용 후에도 Spotless·NullAway·Error Prone 게이트 통과. 실패 시 실패한 모듈·규칙을 확인하고 이 태스크 범위(빌드 도입)에서 해결한다.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build-logic/src/main/kotlin/convention.java-base.gradle.kts build-logic/src/main/kotlin/convention.domain-module.gradle.kts build-logic/src/main/kotlin/convention.query-module.gradle.kts module-common/common-jpa/build.gradle.kts
git commit -m "build: QueryDSL(OpenFeign fork 7.5) 도입 — 도메인·common-jpa Q타입 생성"
```

---

### Task 2: DefaultOrderSearchReader QueryDSL 재작성

**Files:**
- Modify: `module-query/query-order/src/main/java/com/commerce/query/order/DefaultOrderSearchReader.java` (전체 재작성)
- Test: `module-query/query-order/src/test/java/com/commerce/query/order/OrderSearchPersistenceTest.java` (무수정 — 행동 보존 하네스)

**Interfaces:**
- Consumes: Task 1의 Q타입 — `QOrder.order`, `QMember.member`, `com.querydsl.jpa.impl.JPAQueryFactory`.
- Produces: `OrderSearchReader` 계약 불변 — `Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable)`.

이 태스크는 행동 보존 리팩터다 — 새 테스트를 추가하지 않는다. 기존 `OrderSearchPersistenceTest`(4개 시나리오: 이메일+상태 복합 필터, 상태 미지정 전 상태 반환·최신 우선 정렬, 미존재 이메일 빈 페이지, 탈퇴 회원 제외)가 실패-통과 사이클의 통과 기준이다.

- [ ] **Step 1: Reader 전체 재작성**

`DefaultOrderSearchReader.java` 전체를 다음으로 교체:

```java
package com.commerce.query.order;

import static com.commerce.domain.member.domain.QMember.member;
import static com.commerce.domain.order.domain.QOrder.order;

import com.commerce.domain.member.domain.Email;
import com.commerce.domain.order.domain.OrderStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderSearchReader}의 기본 구현이다. */
@Service
class DefaultOrderSearchReader implements OrderSearchReader {

    private final JPAQueryFactory queryFactory;

    DefaultOrderSearchReader(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable) {
        Email emailValue = Email.of(email);

        // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 주문 우선 정렬을 겸한다. 활성 회원 필터는
        // 소프트삭제 조회 기본(삭제 미포함)이자 부분 유니크 인덱스(ux_member_email_active)의 사용 조건이다.
        List<OrderSearchInfo> content = queryFactory
                .select(Projections.constructor(
                        OrderSearchInfo.class,
                        order.id,
                        order.orderNumber,
                        member.id,
                        // 이메일 등치 필터로 모든 행의 이메일이 입력값과 같다 — 컨버터 타입(Email) 경로 대신 상수로 채운다.
                        Expressions.constant(email),
                        order.status,
                        order.fulfillmentStatus,
                        order.payAmount,
                        order.createdAt))
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** 상태 조건이 있으면 등치 식을, 없으면 null을 반환한다 — where는 null 조건을 무시한다. */
    private static @Nullable BooleanExpression statusEq(@Nullable OrderStatus status) {
        return status == null ? null : order.status.eq(status);
    }
}
```

주의:

- `member.email`은 `@Convert` 매핑이라 Q타입에서 단순 경로다 — `.value` 하위 경로가 없다. 비교는 `Email` 값 객체로 한다(파라미터 바인딩 시 컨버터 적용).
- `Expressions.constant(email)`는 `memberEmail`(String) 자리에 입력값을 그대로 채운다 — 필터가 등치라 모든 행에서 동일하다.
- `order.createdAt`은 `BaseTimeEntity` 상속 필드다 — Q타입이 `_super` 위임 필드로 평탄화하므로 직접 접근 가능(Task 1의 `QBaseTimeEntity`가 전제).

- [ ] **Step 2: 포맷 적용**

Run:

```bash
./gradlew :module-query:query-order:spotlessApply
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 영속성 테스트로 행동 보존 검증**

Run (Docker 데몬 필요):

```bash
./gradlew :module-query:query-order:test
```

Expected: `BUILD SUCCESSFUL`, `OrderSearchPersistenceTest` 4개 테스트 전부 PASS. 실패하면 실패 시나리오의 SQL 로그를 확인하고 Step 1의 쿼리 식을 수정한다 — 테스트를 수정하지 않는다.

- [ ] **Step 4: 전체 빌드 검증 (ArchitectureTest 포함)**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` — ArchitectureTest(Q타입이 도메인 패키지에 추가된 뒤에도 경계 규칙 통과) 포함 전 게이트 그린.

- [ ] **Step 5: Commit**

```bash
git add module-query/query-order/src/main/java/com/commerce/query/order/DefaultOrderSearchReader.java
git commit -m "refactor: 주문 검색 조회를 EntityManager JPQL에서 QueryDSL로 전환"
```

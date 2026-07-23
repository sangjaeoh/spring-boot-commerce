# query-order QueryDSL 전환 설계

## 배경

`DefaultOrderSearchReader`가 `EntityManager` + JPQL 문자열 + `%s` 포맷 동적 조건으로 조회한다.
architecture.md의 query 모듈 규칙은 "조회는 QueryDSL을 기본으로 구현한다"이고, 네이티브 쿼리는
방언 종속이 불가피할 때의 최후수단이다. 현재 구현은 방언 종속도 아닌 일반 JPQL이므로 규칙 위반이다.

레포 전체에 QueryDSL이 없어 이번이 첫 도입이다.

## 결정 사항

| 결정 | 선택 | 근거 |
| --- | --- | --- |
| 배포판 | OpenFeign fork (`io.github.openfeign.querydsl`) | 활발한 유지보수, Hibernate 7·Spring Boot 4.1 호환. 원본(com.querydsl)은 2024년 이후 릴리스 정체 |
| Q클래스 생성 위치 | `convention.domain-module` 컨벤션 | 한 곳 수정으로 전 도메인 적용. architecture.md의 도메인 QueryDSL 프래그먼트 규칙과 부합 |
| 결과 매핑 | DTO 생성자 프로젝션 (`Projections.constructor`) | 엔티티 로딩 없이 필요 컬럼만 조회. architecture.md의 타입 안전 프로젝션과 부합 |
| `JPAQueryFactory` | Reader 생성자에서 `new JPAQueryFactory(entityManager)` | 별도 설정 클래스 불필요. 단순함 우선 |

## 빌드 변경

- `gradle/libs.versions.toml`: `querydsl` 버전과 라이브러리 추가 — `querydsl-jpa`, `querydsl-apt`, `querydsl-core` (좌표 `io.github.openfeign.querydsl`). 정확한 최신 버전·classifier는 구현 시 context7로 검증한다.
- `convention.domain-module.gradle.kts`:
  - `annotationProcessor(querydsl-apt)` + `annotationProcessor(jakarta.persistence-api)` — 도메인 모듈 컴파일 시 Q클래스 생성, jar 포함.
  - `implementation(querydsl-core)` — 생성된 Q클래스가 참조하는 타입 컴파일에 필요.
- `convention.query-module.gradle.kts`: `implementation(querydsl-jpa)` — query 모듈이 `JPAQueryFactory`·Q클래스 사용.
- `module-query/query-order/build.gradle.kts`: 변경 없음.

## 구현 변경

`DefaultOrderSearchReader` 재작성:

- JPQL 문자열 상수(`CONTENT_JPQL`·`COUNT_JPQL`·`STATUS_CONDITION`)와 `TypedQuery` 제거.
- 시그니처·`@Service`·`@Transactional(readOnly = true)`·`OrderSearchReader` 계약 불변.
- 콘텐츠 쿼리: `Projections.constructor(OrderSearchInfo.class, ...)` 프로젝션.
  - 조인: `join(member).on(member.id.eq(order.memberId))`.
  - 조건: `member.email.eq(Email.of(email))`, `member.deletedAt.isNull()`, `statusEq(status)`.
  - 정렬: `order.id.desc()` — UUIDv7 ID가 생성 시각 순서라 최신 주문 우선 정렬을 겸한다는 기존 주석 보존.
- 동적 조건: `private static @Nullable BooleanExpression statusEq(@Nullable OrderStatus status)` — null이면 where에서 무시된다.
- 카운트 쿼리: 동일 조건으로 `select(order.count())`. `PageImpl` 유지.

컨버터 타입 주의점:

- `Email`·`Money`는 `@Convert`(AttributeConverter) 매핑이라 Q클래스에서 단순 경로다. `member.email.value` 같은 하위 경로가 없다.
- `memberEmail`(String) 값은 필터 조건과 항상 동일하므로 프로젝션에 `Expressions.constant(email)`로 주입한다 — 컨버터 타입과 String의 불일치를 회피하고 불필요한 컬럼 조회를 제거한다.
- `payAmount`는 `Money` 실제 필드라 프로젝션에서 직접 선택한다.

## 검증 기준

1. `OrderSearchPersistenceTest`가 무수정으로 통과한다 — 행동 보존 증명.
2. 전체 빌드가 통과한다 — Spotless·NullAway·Error Prone 게이트 포함.
3. `ArchitectureTest`가 통과한다.

## 범위 밖

- 도메인 모듈 리포지토리·파생 쿼리의 QueryDSL 전환.
- `OrderSearchReader` 계약·`OrderSearchInfo` 경계 모델 변경.

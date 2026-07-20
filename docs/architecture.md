# Architecture

## 언제

- 새 모듈·앱·도메인을 추가하거나 모듈 경계를 정할 때.
- 어떤 계층이 어떤 계층을 의존해도 되는지 판정이 필요할 때.
- 새 클래스를 어느 패키지에 둘지, 패키지 이름을 어떻게 지을지 정할 때.
- 리포지토리를 어디에 두고, 누가 조회·쓰기하는지(애그리거트 루트·자식 엔티티 접근 범위)를 정할 때. 연관 매핑·생명주기는 → [entity-persistence](entity-persistence.md).

## 규칙

### 컨벤션 플러그인

- 모듈은 빌드 규칙을 직접 조립하지 않고 유형별 컨벤션 플러그인 하나를 적용한다. 계층 의존 규칙은 플러그인이 의존성 해석·컴파일 시점에 강제한다.
  - 규칙이 `build.gradle.kts`마다 복붙되면 드리프트한다. 도구 배선(Spotless·NullAway·Error Prone)을 담는 플러그인은 → [code-quality](code-quality.md)
- 플러그인은 `build-logic/src/main/kotlin/convention.{name}.gradle.kts`로 둔다.

  | 플러그인                     | 적용 대상       | 소유 규칙                                                       |
  | ---------------------------- | --------------- | -------------------------------------------------------------- |
  | `convention.domain-module`   | `domain-{name}` | JPA·QueryDSL + 허용 의존 화이트리스트(domain-shared·common-core·jpa·messaging) |
  | `convention.app-module`      | `app-{name}`    | Spring Boot 실행 조립 — domains·infra·external·common 의존 허용 |
  | `convention.infra-module`    | `infra-{tech}`  | common만 의존                                                   |
  | `convention.external-module` | `external-{name}` | 구현 대상 domain + common만 의존                             |
  | `convention.common-module`   | `common-{role}` | core 방향 단방향(web→auth만 허용), core는 의존 제로            |

- 계층 컨벤션 플러그인은 그 계층의 첫 모듈을 만들 때 함께 구현한다. 모듈 없는 계층의 플러그인을 선제 구현하지 않는다.
- 계층 컨벤션 플러그인은 `convention.java-base`(Spotless·NullAway·Error Prone)와 `convention.java-common`(금지 의존성 차단)을 apply한다. 따라서 모듈은 계층 플러그인 하나만 적용해도 품질 게이트가 딸려온다(도구 상세는 → [code-quality](code-quality.md)).
- 모듈 간 의존은 `implementation`이 기본이다. `api`는 그 타입이 자기 모듈 공개 시그니처에 등장해 소비자 재노출이 의도된 때만 쓴다.
- 새 모듈은 `module-{layer}/{prefix}-{name}` 디렉터리에 `build.gradle.kts` 하나(해당 유형 컨벤션 플러그인만 적용)로 만들고 settings에 등록한다.
  - 예: `module-domains/domain-order/build.gradle.kts`(`convention.domain-module`만 적용). 래퍼·CI·플러그인 구현 등 빌드 하네스 자체는 이미 구성된 것으로 전제한다.

### 모듈 지도 (6계층)

- 모듈은 아래 계층 경계와 의존 방향을 지킨다. 의존은 항상 한 방향으로만 흐른다(앱 → 도메인·인프라 → 공용).
  - 경계를 컴파일 의존성 자체로 강제한다 — 단일 모듈 패키지 분리는 리뷰로만 지켜져 타 도메인 참조가 조용히 새므로 기각한다. 도메인이 비대해지면 내부 코드 수정 없이 독립 MSA로 빌드·배포하는 것이 목표다.

  | 계층               | 역할                                                                  | 의존 가능 대상                                             |
  | ------------------ | --------------------------------------------------------------------- | ---------------------------------------------------------- |
  | `module-apps/`     | 실행 Spring Boot 앱. 조립·오케스트레이션·표현                         | domains, infra, external, common 전부                      |
  | `module-domains/`  | 도메인 로직·엔티티·리포지토리. 타 도메인·infra·external·web 의존 금지 | domain-shared, common-core, common-jpa, common-messaging   |
  | `module-infra/`    | 기술 구현체(Redis, 메시징 transport)                                  | common                                                     |
  | `module-external/` | 외부 시스템 어댑터(PG·알림) — 도메인 소유 포트를 구현                 | 구현 대상 domain, common                                   |
  | `module-common/`   | 공용 계층 — core(순수)·jpa·messaging·auth·web                         | core는 제로 의존, 나머지는 core 방향 단방향(web→auth 허용) |
  | `module-tests/`    | 아키텍처 테스트 등 크로스 모듈 검증                                    | 전 모듈(테스트 전용, 아무도 의존 안 함)                     |

- 모듈 경계는 빌드가 컴파일·테스트 시점에 강제한다(강제 대상 전체 목록은 아래 빌드가 강제하는 불변식).
- `module-tests/`는 이 의존 흐름 밖의 테스트 전용 계층이다.

### infra vs external 판정

- 계약의 도메인 귀속으로 가른다. 벤더 선택·교체가 특정 도메인의 비즈니스 결정이면 그 도메인이 포트를 소유하고 external이 구현한다. 도메인 의미 없이 여러 도메인·앱이 소비하는 범용 기술이면 common이 포트를 소유하고 infra가 구현한다.
  - 예: PG·알림·메일은 external(도메인 포트 구현, 벤더 교체 대상). Redis 캐시·메시징 transport·오브젝트 스토리지는 infra(포트 소유자가 common).
  - 외부 소유 시스템 여부로 가르지 않는다 — S3는 외부 시스템이지만 도메인 의미가 없어 infra다.
  - infra 서브모듈은 `infra-{tech}`, external 서브모듈은 `external-{name}`으로 이름 짓는다.

### 앱 구성

- 앱은 기본 4종이고 최소 구성은 `app-api`·`app-migration` 둘이다. 앱마다 하나의 실행 모듈(`app-{name}`)이다.

  | 앱              | 책임                  | 데이터 접근                                          |
  | --------------- | --------------------- | ---------------------------------------------------- |
  | `app-api`       | API·파사드            | 도메인 Reader·Facade 경유(경계는 Info)               |
  | `app-admin`     | 어드민·백오피스       | 격리 구역 `infrastructure/query`에서 크로스 스키마 조회 |
  | `app-batch`     | 배치·정리 잡          | 격리 구역 `infrastructure/reader`에서 엔티티 접근    |
  | `app-migration` | Flyway 독립 실행기    | 런타임 데이터 접근 없음                              |

- 마이그레이션 실행은 `app-migration`이 전담한다. 실행 앱은 부팅 시 Flyway를 실행하지 않는다.
- 실행 앱은 아래 설정 불변식을 둔다. 다수 규칙의 전제다.
  - `spring.jpa.open-in-view: false` — 경계 밖 지연로딩을 런타임에 차단한다(경계 원칙의 전제).
  - `spring.jpa.hibernate.ddl-auto: validate` — 스키마는 Flyway가 소유하고 기동 시 엔티티↔DDL 일치만 검증한다(마이그레이션·인덱스·`@Version` 규칙의 전제).

### 앱 모듈 구조 (`app-{name}/`)

- 앱 모듈은 아래 서브 패키지로 구성한다.
  - `web` — 컨트롤러·DTO. API 버전별 `web/v{n}` 분리, 그 아래는 리소스별 슬라이스로 나눠 각 슬라이스가 컨트롤러와 `request`·`response`를 소유한다.
    - 버전을 바깥 축, 리소스를 안쪽 축에 둬 `/api/v{n}/{리소스}` URL 경로를 패키지로 미러링한다. `web`은 HTTP 계열 전송(REST·WebSocket·SSE)을 담고, 비-web 진입점은 형제 패키지로 둔다.
  - `facade` — 도메인 조립·크로스 도메인 조율.
    - 파사드가 합성해 web으로 내보내는 `*View` record는 `facade/view`가 소유한다.
  - `event/listener` — 크로스 도메인 이벤트 소비.
  - `infrastructure/query`(admin) · `infrastructure/reader`(batch) — 엔티티 의존을 허용하는 격리 구역. canonical 경로는 `app.admin.infrastructure.query` · `app.batch.infrastructure.reader`다.
- 관객별 인가는 앱 소유 메타 애노테이션으로 표시한다 — `@Admin`(관리자 전용)·`@Authenticated`(로그인 전용)·`@Anonymous`(익명 전용). 앱의 `web/auth`가 소유하고 각각 `@PreAuthorize`를 합성하며 클래스·메서드 레벨 모두에 붙는다(`@Target({TYPE, METHOD})`).
  - 인가는 이중 게이트다 — URL 필터 체인(`SecurityConfig`)이 1차, 이 마커를 메서드 시큐리티(`@EnableMethodSecurity`)가 2차로 강제한다. 미인증 익명은 401, 권한 부족은 403이다. 컨트롤러에서 던져지는 접근 거부는 전역 핸들러가 같은 403 계약으로 매핑한다.
  - 마커는 제약만 더하고 개방은 못 한다. 공개(permitAll) 표면은 마커 없이 필터 체인의 공개 경로 열거가 소유한다.
- 어드민 표면은 `web/v{n}/admin/{리소스}` 슬라이스의 `{Domain}AdminController`가 소유한다 — 클래스 레벨 `@Admin`, URL 프리픽스 `/api/v{n}/admin/`. 어드민 URL 네임스페이스는 게이트웨이·방화벽 차단 축이 된다.
  - 어드민은 전용 컨트롤러로 응집하므로 `@Admin`은 클래스 레벨로만 선언한다(메서드 레벨 금지).
- 로그인·익명 표면은 관객이 한 컨트롤러에 섞일 수 있어 `@Authenticated`·`@Anonymous`를 메서드 레벨로도 선언한다.
  - 예: 회원 컨트롤러는 공개 가입(`@Anonymous`)과 본인 조회(`@Authenticated`)를 메서드별로 표시한다. 어드민만 관객별 전용 컨트롤러로 쪼개고, 로그인·익명은 메서드 레벨 표시로 컨트롤러 분할을 강제하지 않는다.
- 두 표면이 공유하는 응답 DTO는 본인·공개 슬라이스가 소유하고 어드민 슬라이스가 참조한다.
- 컨트롤러 핸들러와 request/response는 클라이언트 명세를 어노테이션으로 명시한다 — 핸들러에 `@Operation`·성공/적용 에러 `@ApiResponse`, 핸들러의 `@RequestParam`·`@PathVariable` 파라미터에 `@Parameter(description)`, request/response 타입·컴포넌트에 `@Schema`. 자동 생성이 못 만드는 의미·에러 계약을 코드에 둔다.
  - 에러 응답은 도달 가능한 상태만 적는다 — 인증 표면(`AuthUser`·관객 마커)은 401·403, 검증(`@Valid`)은 400, 타 회원 리소스는 404, 도메인·파사드 `ErrorCode.status()`는 그 상태. 전역 핸들러가 problem+json으로 매핑한다(같은 상태의 여러 원인은 한 응답으로 묶는다).
  - 인증 강제 표면의 bearer 요구와 토큰 주입 파라미터(`AuthUser`) 숨김은 `OpenApiConfig`가 코드에서 도출한다. 이 둘은 어노테이션으로 반복하지 않는다.
- 페이징 GET은 파라미터를 common-web `PaginationRequest`(`@Valid @ParameterObject`)로 받고, 페이지 응답 DTO는 목록 옆에 common-web `PaginationResponse`를 `page` 컴포넌트로 싣는다. 도메인 `Pageable`은 0-based를 유지하며 요청 변환은 `zeroBasedPage()`, 응답 +1 보정은 `PaginationResponse.from(Page)`가 소유한다.
  - 페이지 번호 계약이 요청·응답 대칭으로 1부터 시작하고, 0/1-based 변환·페이지 메타 필드가 web 경계의 두 타입에만 갇힌다.

### 도메인 모듈 구조 (`domain-{name}/`)

- 도메인 모듈은 아래 서브 패키지로 구성한다.
  - `entity` — JPA 엔티티·값 객체(VO)·그 `AttributeConverter`(설계·연관·생명주기·VO 매핑은 → [entity-persistence](entity-persistence.md)). JPA 매핑 클래스(`@Entity`·`@MappedSuperclass`)는 모듈 밖 시그니처 등장 금지, 값 타입(enum·record·`@Embeddable` VO)은 경계 계약(명령 입력·Info·포트)으로 통과 허용.
  - `info` — 경계용 불변 record(`Info` 정의는 → [coding-conventions](coding-conventions.md)).
  - `repository` — Spring Data + QueryDSL(배치·접근 범위는 아래 리포지토리 접근 범위).
  - `service` — Reader·Appender·Modifier·Remover·Processor·Validator(역할 접미사는 → [coding-conventions](coding-conventions.md)).
  - `port` — 소비하는 외부 기능의 벤더 중립 인터페이스.
  - `event` — 도메인 이벤트 record.
  - `exception` — `{Name}ErrorCode` + 예외.
  - 그 외 모듈 소유: `db/migration/{name}/`. 마이그레이션은 `V{n}__{설명}.sql`로 명명하고 1변경 1파일로 쓴다(적용된 파일은 수정하지 않고 새 버전을 추가한다).
- `domain-shared`는 둘 이상의 도메인이 쓰는 값 객체와 그 `AttributeConverter`를 소유한다. 엔티티·리포지토리·마이그레이션은 두지 않는다.
  - 소비 도메인이 하나뿐인 값 객체는 그 도메인의 `entity`가 소유한다.
- 엔티티를 두는 도메인마다 PostgreSQL 스키마 하나를 소유한다.
  - 도메인 경계가 스키마로 드러나 크로스 도메인 조인·FK 유혹이 구조적으로 차단되고, MSA 분리 시 떼어낼 조인이 없다. 단일 스키마 공유는 분리 시 떼어낼 조인이 남아 기각한다.
  - 새 도메인은 `db/migration/{name}/`에 스키마 생성 + 테이블 DDL을 두고, 그 스키마의 Flyway 인스턴스를 `SchemaFlywayFactory`(common-jpa)에 등록해 `app-migration`이 스키마별로 실행한다. 누락하면 빌드는 green인데 `ddl-auto=validate` 기동이 스키마 불일치로 실패한다.

### common 모듈 배치

- 공용 코드는 아래 역할 표에 맞는 common 모듈에 둔다. 도메인 로직·도메인 지식은 어느 common에도 두지 않는다.

  | 모듈               | 담는 것                                                                        |
  | ------------------ | ------------------------------------------------------------------------------ |
  | `common-core`      | 프레임워크 의존 제로의 순수 코드 — UUIDv7 생성기·시간·벤더 중립 애노테이션·`BaseException`·`ErrorCode` |
  | `common-jpa`       | JPA 공통 지원 — Auditing·`SchemaFlywayFactory`                                 |
  | `common-messaging` | 발행 포트(`MessagePublisher`)·도메인 이벤트 마커(`DomainEvent`)·아웃박스·멱등 소비 지원(transport 구현은 infra) |
  | `common-auth`      | 토큰(JWT) 발급·검증 메커니즘 — 주체·커스텀 클레임(문자열 맵)만 다루고 클레임 어휘는 소비자 소유(웹 필터는 common-web) |
  | `common-web`       | 웹 공통 — 인증·멱등 필터·`AuthUser`·ProblemDetail 핸들러·공용 validator 승격처·web 공통 DTO(`PaginationRequest`·`PaginationResponse`) |

- common 모듈은 첫 수요가 생길 때 만든다. 소비자 없는 모듈을 선제 생성하지 않는다.
- 배치가 애매하면 더 좁은 의존의 모듈을 택한다(core에 갈 수 있으면 core로).
  - 역할별 분할의 목적이 의존 가능 범위 축소라서다. 단일 util 모듈은 도메인이 web 타입에 의존하는 오염을 막지 못해 기각한다.

### 아키텍처 테스트 모듈

- 아키텍처 테스트는 `module-tests/test-architecture` 모듈이 소유한다. 앱이나 애플리케이션 모듈 안에 두지 않는다.
  - 컴파일된 클래스 그래프로 경계를 검증하려면 검사 대상 전 모듈이 그 classpath에 있어야 한다. test-architecture는 전 모듈을 `testImplementation`으로 의존해 그래프를 집약한다.
  - 앱 안에 두면 커버리지가 그 앱의 의존으로 좁혀져 그 앱이 안 쓰는 모듈이 조용히 검사에서 빠진다. 앱이 여럿이면 어느 앱도 전 모듈을 의존한다고 보장되지 않는다.
- test-architecture는 계층 플러그인이 아니라 품질 게이트 플러그인(`convention.java-base`·`convention.java-common`)만 적용한다.
  - 전 계층을 가로질러 의존하므로 계층 화이트리스트를 걸 수 없다.
- 새 모듈이 검사에서 누락되지 않도록 test-architecture의 의존을 모듈 목록에서 파생시킨다.

### 패키지 네이밍·배치

- 패키지명은 소문자 단수(`entity`·`info`·`repository`)로 쓴다.
- 베이스 패키지(1-depth)에 클래스를 방치하지 않는다. 성격에 맞는 서브 패키지로 분류한다.
  - 스프링 진입점 클래스와 `package-info.java`는 예외.
- 모든 모듈 베이스 패키지에 `package-info.java`를 둔다. 여기에 `@NullMarked`를 붙인다(null 계약은 → [code-quality](code-quality.md)).
- 커스텀 예외(`*Exception`)와 `{Name}ErrorCode` enum은 발생 위치와 무관하게 해당 모듈의 `exception` 패키지에 모은다.
- `@Configuration`(`*Config`)·AOP(`*Aspect`) 클래스는 성격 패키지 `config`·`aop`에 둔다.
- 테스트 소스는 `src/test`에서 대상의 패키지를 미러링한다. `Fixture`는 `src/test`가 소유한다.

### 리포지토리 접근 범위

- 도메인의 영속 포트는 Spring Data 인터페이스 그 자체다. 감싸는 별도 포트 인터페이스를 만들지 않는다.
- Spring Data 인터페이스는 도메인 모듈의 `repository` 패키지에만 평탄하게 둔다. `custom/` 하위 패키지를 만들지 않는다.
- 엔티티를 반환하는 조회 메서드는 도메인 모듈 경계 밖으로 노출하지 않는다. 경계를 넘는 조회는 Info로 변환하고, apps의 리포지토리 직접 접근은 빌드가 막는다.
  - Spring Data 인터페이스는 서비스 주입을 위해 언어상 public이지만, 외부 소비는 경계 강제가 막는다. 인터페이스를 package-private로 만들면 별도 패키지의 서비스가 주입 못 해 컴파일이 깨진다.
- 애그리거트는 함께 커밋·롤백돼야 하는 불변식·생명주기의 일관성 단위로 묶고, 그 진입점을 루트로 정한다.
  - 예: 주문-주문라인은 주문이 루트(라인은 주문 없이 존재·변경되지 않는다). 반례: 독립 생명주기·독립 불변식을 갖는 엔티티는 각자 다른 애그리거트 루트이며 ID 참조로만 잇는다.
- 리포지토리는 애그리거트 루트마다 하나 둔다. 자식 엔티티 전용 리포지토리는 아래 성능 예외로만 둔다.
- 외부는 루트를 통해서만 내부 엔티티를 변경한다. 이는 쓰기 기준이다.
  - 애그리거트 간 참조는 ID 값만 보관한다(물리 FK 금지·연관 규칙은 → [entity-persistence](entity-persistence.md)).
  - 도메인 서비스의 한 트랜잭션은 하나의 애그리거트만 변경한다.
- 자식 엔티티 리포지토리의 성능 예외는 조회와 벌크에만 좁게 연다.
  - 자식 엔티티 직접 조회는 불변식을 깨지 않으므로 부모 전체 로딩 OOM·N+1을 피하려 리포지토리·QueryDSL로 직접 조회한다.
  - 자식 리포지토리를 통한 벌크 `UPDATE`·`DELETE`·대용량 `INSERT`도 허용한다. 막는 것은 자식 단건 `INSERT`뿐이다(신규 자식 생성 메커니즘은 → [entity-persistence](entity-persistence.md)).
- 소프트삭제(`deletedAt`) 엔티티는 삭제 여부를 거르지 않는 base `JpaRepository` finder 직접 호출을 금지한다(`findById`·`existsById`·`findAll`·`findAllById`·`count`·`getReferenceById`).
  - 삭제분을 거르지 않으므로 빌드가 막는다. 활성-only 파생 쿼리는 이름에 활성 필터를 담는다(`...DeletedAtIsNull`). 삭제분을 일부러 포함하는 조회만 이름에 `IncludingDeleted`를 붙인다.
  - `deletedAt`이 없는(소프트삭제 미지원) 엔티티는 base `findById` 등을 그대로 쓴다 — 삭제분 노출 위험이 없다.
- 리포지토리 조회는 아래 순서로 표현 가능한 최소 단계를 쓴다. 아래로 갈수록 표현력은 늘고 이름-스펙성·가독성은 준다.
  1. 파생 쿼리 메서드(`findBy…`) — 정적 단순 조건(등호·`And`/`Or`·정렬·활성 필터). 이름이 곧 스펙이라 우선한다.
  2. `@Query`(JPQL) — 정적이나 파생으로는 이름이 비대해지거나(대략 조건 3개 초과) 파생 파서가 못 푸는 것: 명시적 조인·`IN`·서브쿼리·DTO 프로젝션·벌크 `@Modifying`.
  3. QueryDSL 프래그먼트 — 런타임 조건 조합(선택적 필터)·커서 페이지네이션·타입 안전 프로젝션·복잡 동적 조인. 컴파일 타임 타입 안전이 필요할 때.
  - 네이티브 SQL은 방언 종속이라 최후수단이며 리뷰 게이트다.
  - QueryDSL 프래그먼트도 `repository` 패키지에 평탄하게 둔다(`custom/` 금지는 위와 동일).
  - 메서드명 규칙은 → [coding-conventions](coding-conventions.md)의 네이밍이 소유한다.

### 경계 원칙

- 데이터 반환은 내부는 엔티티, 경계는 Info, 명령은 ID다.
  - 도메인 모듈 내부(service↔repository)는 엔티티 그대로. 모듈 밖으로 나가는 조회는 도메인 소유 `info/` record로 변환한다. 명령은 최소 결과(ID 등)만 반환한다.
  - `Page<Entity>`는 경계에서 `Page<Info>`로 변환해 내보낸다(엔티티가 경계를 넘지 않는다).
  - LazyInit·의도치 않은 dirty checking·API-엔티티 결합·MSA 분리 시 계약 부재를 차단한다. OSIV off(`open-in-view: false`)로 경계 밖 지연로딩을 런타임에 차단한다(엔티티 비노출의 빌드 강제는 모듈 지도가 소유).
  - 예외 격리 구역: admin `infrastructure/query`, batch `infrastructure/reader`는 엔티티 의존을 허용한다. 격리 구역의 크로스 스키마 조회는 읽기 전용이며, admin·batch는 MSA 분리 대상에서 제외한다(분리 시 read model로 대체).
- 외부 기능 포트 소유권은 소비 도메인에 둔다. 도메인 의미 없는 기술 포트는 common이 소유하고 infra가 구현한다.
  - 외부 기능 포트(예: `PaymentGateway`)는 소비 도메인의 `port/`가 소유하고 external이 구현한다(방향: external → domain). 포트명은 벤더 중립("Toss" 금지).
  - 인터페이스와 구현이 같은 모듈이면 DIP가 아니다.

### 트랜잭션 경계

- 트랜잭션 경계는 도메인 서비스의 쓰기 메서드(`Appender`·`Modifier`·`Remover`·`Processor`)가 소유한다. `@Transactional`을 여기에 붙인다.
- 외부 시스템 호출 등 취소 불가능한 부작용을 내는 쓰기는 그 부작용을 트랜잭션 밖에서 수행하고 결과 영속만 프로그램적 `TransactionTemplate`으로 좁힌다. 영속이 실패하면 부작용을 보상한다.
  - 부작용을 `@Transactional` 안에 두면 커밋 실패 시 부작용이 롤백 없이 고아가 된다. 이 경우만 애노테이션 대신 프로그램적 트랜잭션을 쓰고, 호출자는 이 메서드를 트랜잭션 밖에서 부른다(안이면 `REQUIRED`가 합류해 부작용이 다시 트랜잭션에 묶인다). 한 트랜잭션 하나의 애그리거트는 유지된다(영속은 자기 애그리거트만 바꾼다).
- facade는 트랜잭션을 열지 않는다. 도메인 서비스를 조립·조율만 하고, 각 도메인 서비스가 자기 트랜잭션을 연다.
  - facade에 트랜잭션을 걸면 한 트랜잭션이 여러 애그리거트를 바꿔 "한 트랜잭션 하나의 애그리거트" 원칙과 충돌한다.
- 여러 도메인을 바꾸는 조율은 동기 트랜잭션이 아니라 도메인 이벤트로 최종일관성을 취한다(발행 도메인이 커밋 후 발행, 소비는 멱등). 아웃박스·보상 등 구체 코레오그래피 패턴은 프로젝트 결정으로 이 가이드 범위 밖이다.
- 조회는 `Reader`가 `@Transactional(readOnly = true)` 안에서 Info 변환까지 완료한다.
  - OSIV off(경계 원칙이 소유)라 경계 밖 지연로딩이 막히므로, 엔티티→Info 변환은 트랜잭션 안에서 끝나야 `LazyInitializationException`을 피한다.

### 빌드가 강제하는 불변식

- 아래는 리뷰가 아니라 빌드가 막는 경계다. 에이전트는 코드 생성 후 이 목록으로 자기검증하고, 채택 팀은 이 목록으로 강제 장치 구현 완료를 대조한다(각 항목에 강제 장치를 매핑). 강제 장치(컨벤션 플러그인·아키텍처 테스트) 자체는 빌드 하네스가 소유한다.
  - 계층 의존 방향(모듈 지도) — 컨벤션 플러그인(컴파일 시점).
  - JPA 매핑 클래스(`@Entity`·`@MappedSuperclass`)의 모듈 밖 시그니처 등장 금지·타입 위치·apps의 리포지토리 직접 접근 금지·base `JpaRepository` finder 직접 호출 금지(소프트삭제 엔티티에 한함) — 아키텍처 테스트(테스트 시점, → 아키텍처 테스트 모듈).
  - web 컨트롤러 핸들러의 `@Operation`·`@ApiResponse` 선언, 핸들러 `@RequestParam`·`@PathVariable` 파라미터의 `@Parameter(description)` 선언, request/response 타입·컴포넌트의 `@Schema` 선언(존재만 검사, 에러 상태 집합의 정확성은 리뷰) — 아키텍처 테스트(테스트 시점, → 아키텍처 테스트 모듈).
  - web 컨트롤러 핸들러의 int·Integer `@RequestParam` 직접 선언 금지(페이징 파라미터는 common-web `PaginationRequest`) — 아키텍처 테스트(테스트 시점, → 아키텍처 테스트 모듈).
  - 어드민 표면 정렬 — admin 패키지 소속·`*AdminController` 네이밍·클래스 레벨 `@Admin`·`/api/v{n}/admin/` URL 프리픽스 네 마커의 동시 충족, 메서드 레벨 `@Admin` 금지 — 아키텍처 테스트(테스트 시점, → 아키텍처 테스트 모듈).
  - 각 모듈 베이스 패키지 `@NullMarked`·null 계약·포맷·정적분석 — Spotless·NullAway·Error Prone(→ [code-quality](code-quality.md)).
  - 금지 의존성(Lombok·H2) — 컨벤션 플러그인.

# Architecture

## 언제

- 새 모듈·앱·도메인을 추가하거나 모듈 경계를 정할 때.
- 어떤 계층이 어떤 계층을 의존해도 되는지 판정이 필요할 때.
- 새 클래스를 어느 패키지에 둘지, 패키지 이름을 어떻게 지을지 정할 때.
- 리포지토리를 어디에 두고, 누가 조회·쓰기하는지를 정할 때.

## 규칙

### 컨벤션 플러그인

- 모듈은 유형별 컨벤션 플러그인 하나로 규칙을 적용한다.
  - 규칙을 `build.gradle.kts`마다 직접 복붙하지 않는다.
  - 품질 도구(Spotless·NullAway·Error Prone)는 → [code-quality](code-quality.md)로 관리한다.
- 플러그인은 `build-logic/src/main/kotlin/convention.{name}.gradle.kts`에 둔다.

  | 플러그인                     | 적용 대상       | 소유 규칙                                                                 |
  | ---------------------------- | --------------- | ------------------------------------------------------------------------ |
  | `convention.domain-module`   | `domain-{name}` | `domain-shared`, `common-core`, `common-jpa`, `common-messaging` 모듈만 의존 가능 |
  | `convention.app-module`      | `app-{name}`    | `domain-{name}`·`infra-{name}`·`external-{name}`·`common-{name}` 모듈만 의존 가능 |
  | `convention.infra-module`    | `infra-{name}`  | `common-{name}` 모듈만 의존 가능                  |
  | `convention.external-module` | `external-{name}` | 구현 대상 `domain-{name}`과 `common-{name}` 모듈만 의존 가능 |
  | `convention.common-module`   | `common-{name}` | `common-core` 방향으로만 단방향 의존 가능|

- 계층 컨벤션 플러그인은 첫 모듈 생성 시 함께 구현한다.
- `convention.java-base`와 `convention.java-common`을 적용해 품질 게이트를 함께 붙인다(도구 상세는 → [code-quality](code-quality.md)).
- 모듈 간 의존은 기본적으로 `implementation`을 쓰고, `api`는 공개 시그니처 재노출이 필요할 때만 사용한다.
- 새 모듈은 `module-{name}/{prefix}-{name}` 아래 `build.gradle.kts` 하나로 만들고 settings에 등록한다.
  - 예: `module-domains/domain-order/build.gradle.kts`에 `convention.domain-module`만 적용한다.

### 모듈 지도 (6계층)

- 모듈은 아래 계층 경계와 의존 방향을 지킨다. 의존은 한 방향으로만 흐른다.
  - 경계는 컴파일 의존성으로 강제한다.

  | 계층 | 역할 | 의존 가능 대상 |
  | ---- | ---- | -------------- |
  | `module-apps/` | 실행 애플리케이션 계층 | `domain-{name}`·`infra-{name}`·`external-{name}`·`common-{name}` |
  | `module-domains/` | 도메인 계층 | `domain-shared`·`common-{name}` |
  | `module-infra/` | 인프라 계층 | `common-{name}` |
  | `module-external/` | 외부 연동 계층 | 구현 대상 `domain-{name}`·`common-{name}` |
  | `module-common/` | 공용 계층 | `common-core`는 제로 의존, 나머지는 `common-core` 방향으로만 단방향 의존 |
  | `module-tests/` | 테스트 계층 | 모든 모듈 |

- 모듈 경계는 빌드가 컴파일·테스트 시점에 강제한다.
- `module-tests/`는 이 의존 흐름 밖의 테스트 전용 계층이다.

### infra vs external 판정

- 도메인 비즈니스와 직접 연결된 기능은 그 도메인이 포트를 소유하고 external이 구현한다.
- 도메인 의미가 없는 공통 기능은 common이 포트를 소유하고 infra가 구현한다.
- infra 서브모듈은 `infra-{name}`, external 서브모듈은 `external-{name}`으로 이름 짓는다.

### 앱 구성

- 앱은 기본 4종이며 최소 구성은 `app-api`와 `app-migration`이다. 각 앱은 하나의 실행 모듈(`app-{name}`)이다.

  | 앱              | 책임              | 데이터 접근                                   |
  | --------------- | ----------------- | --------------------------------------------- |
  | `app-api`       | API·파사드        | 도메인 Reader·Facade 경유                     |
  | `app-admin`     | 어드민·백오피스   | 격리 구역 `infrastructure/query`에서 조회     |
  | `app-batch`     | 배치·정리 잡      | 격리 구역 `infrastructure/reader`에서 접근   |
  | `app-migration` | Flyway 독립 실행기 | 런타임 데이터 접근 없음                       |

- `app-migration`이 마이그레이션을 실행한다. 실행 앱은 부팅 시 Flyway를 실행하지 않는다.
- 실행 앱은 다음 불변식을 따른다.
  - `spring.jpa.open-in-view: false` — 경계 밖 지연로딩을 막는다.
  - `spring.jpa.hibernate.ddl-auto: validate` — 스키마는 Flyway가 관리한다.

### 앱 모듈 구조 (`app-{name}/`)

- 앱 모듈은 역할별로 패키지를 중심에 두고 구성한다. 공통 규칙은 하위 `앱 공통` 섹션에 둔다.

#### `app-api`
- 중심 패키지: `web`, `facade`
- `web/v{n}`은 API 버전과 리소스별 슬라이스를 패키지로 분리한다.
- `facade`는 도메인 Reader/Writer를 조합해 응답을 만든다.
- `infrastructure`는 도메인 경계를 직접 통과하지 않고 서비스를 거친다.

#### `app-admin`
- 중심 패키지: `web`, `infrastructure/query`
- `web/v{n}/admin/{리소스}` 슬라이스가 어드민 컨트롤러를 소유한다.
- 어드민 컨트롤러는 클래스 레벨 `@Admin`만 사용한다.
- 어드민 URL 네임스페이스는 게이트웨이와 방화벽 차단 축이다.
- `infrastructure/query`는 격리된 조회 영역으로 엔티티 의존을 허용한다. 표준 경로는 `app.admin.infrastructure.query`다.

#### `app-batch`
- 중심 패키지: `infrastructure/reader`
- 배치 작업은 앱 고유 진입점 패키지가 소유한다.
- `infrastructure/reader`는 배치 전용 읽기 영역으로 엔티티 의존을 허용한다.
- `infrastructure/reader` 표준 경로는 `app.batch.infrastructure.reader`다.
- `web`은 배치 진입점이 필요할 때만 둔다.

#### `app-migration`
- 중심 패키지: `db/migration`, `config`
- Flyway 마이그레이션만 실행한다.
- 런타임 데이터 접근을 하지 않는다.

### 앱 모듈 공통
- `web`은 HTTP 전송(REST·WebSocket·SSE)을 담당한다. 비-web 진입점은 형제 패키지로 둔다.
- `web/v{n}`은 버전을 바깥 축, 리소스를 안쪽 축으로 구성해 `/api/v{n}/{리소스}` URL을 미러링한다.
- `facade`는 도메인 조립과 크로스 도메인 조율, `*View` 응답은 `facade/view`가 소유한다.
- `event/listener`는 앱이 이벤트 소비를 포함할 때 소유한다.
- `web/auth`는 앱 소유 메타 애노테이션 `@Admin`, `@Authenticated`, `@Anonymous`를 제공한다.
- `@Admin`은 클래스 레벨 전용, `@Authenticated`·`@Anonymous`는 필요 시 메서드 레벨에 쓸 수 있다.
- 앱 소유 마커는 제약만 더하고 공개 표면은 `permitAll` 공개 경로로 둔다.
- `SecurityConfig` 필터 체인과 메서드 시큐리티는 이중 게이트로 401/403을 구분한다.
- 동일 응답 DTO를 공유하는 경우 본인/공개 슬라이스가 소유하고 어드민이 참조한다.
- `web` 핸들러와 request/response는 `@Operation`, `@ApiResponse`, `@Parameter(description)`, `@Schema`로 명시한다.
- `OpenApiConfig`는 bearer 요구와 `AuthUser` 주입을 반복하지 않는다.
- 페이징 GET은 common-web `PaginationRequest`(`@Valid @ParameterObject`)를 사용한다.
- 클라이언트 요청은 1-based 페이지를 받는다. 도메인 `Pageable`은 0-based를 유지한다.
- 페이지 응답은 common-web `PaginationResponse`의 `page` 컴포넌트로 싣고, 도메인 0-based를 1-based로 보정한다.
- 요청 변환은 `PaginationRequest.zeroBasedPage()`가, 응답 보정은 `PaginationResponse.from(Page)`가 소유한다.

### 도메인 모듈 구조 (`domain-{name}/`)

- 중심 패키지: `entity`, `info`, `repository`, `service`, `port`, `event`, `exception`
  - `entity` — JPA 매핑 타입, 값 객체, `AttributeConverter`. JPA 매핑 클래스는 모듈 밖 노출 금지.
  - `info` — 경계용 불변 record.
  - `repository` — Spring Data + QueryDSL 리포지토리.
  - `service` — Reader, Appender, Modifier, Remover, Processor, Validator.
  - `port` — 외부 기능의 벤더 중립 인터페이스.
  - `event` — 도메인 이벤트 record.
  - `exception` — `{Name}ErrorCode`와 예외.
- 도메인별 독립 스키마를 소유한다.
- 마이그레이션은 도메인별 `db/migration/{name}/`에 두고, `V{n}__{설명}.sql` 형식을 사용한다.

#### 도메인 모듈 공통

- `domain-shared`는 두 도메인 이상이 쓰는 값 객체와 `AttributeConverter`만 소유한다.
- 하나의 도메인만 사용하는 값 객체는 해당 도메인의 `entity`가 소유한다.
- 각 도메인의 Flyway 인스턴스는 `SchemaFlywayFactory`(`common-jpa`)에 등록하고 `app-migration`이 실행한다.
- 적용된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.

### common 모듈 구조

- 공용 코드는 역할에 따라 common 모듈에 둔다.
- 도메인 로직과 도메인 지식은 common에 두지 않는다.

#### common 모듈 공통 규칙

- common 모듈은 실제 필요가 생긴 때에만 만든다.
- 소비자 없는 모듈은 미리 만들지 않는다.
- 의존 범위가 더 좁은 모듈이 가능하면 그쪽을 택한다.
- 여러 도메인에서 공통으로 쓰이는 책임만 공용 모듈로 분리한다.

| 모듈 | 책임 |
| --- | --- |
| `common-core` | 프레임워크 의존이 없는 기본 공용 코드 |
| `common-jpa` | 여러 모듈이 공유하는 영속성 지원 책임 |
| `common-messaging` | 이벤트·메시지 전달 같은 교차 관심사 |
| `common-auth` | 인증·인가 공통 책임 |
| `common-web` | 웹 공통 책임 |

### 공통 규칙

- 아키텍처 테스트는 모듈 경계와 의존 방향을 검증하는 품질 게이트다.
- 새 모듈이 누락되지 않도록 검증 대상은 모듈 목록에서 파생한다.

### 아키텍처 테스트 모듈

- 아키텍처 테스트는 `module-tests/test-architecture` 모듈이 소유한다.
- 앱이나 애플리케이션 모듈 안에 두지 않는다.
- 테스트는 전체 모듈 그래프를 기준으로 경계를 검증한다.
- `test-architecture`는 계층 규칙이 아니라 품질 게이트 플러그인(`convention.java-base`, `convention.java-common`)만 적용한다.

### 패키지 네이밍·배치

- 패키지명은 목적을 분명히 드러내는 간결한 이름(소문자 단수형 권장)을 사용한다.
- 최상위 패키지에 일반 클래스를 두지 말고 책임별 하위 패키지로 구성한다. 애플리케이션 진입점은 예외로 둔다.
- 각 모듈의 루트에는 모듈 경계와 계약을 드러내는 표식을 둔다(모듈 단위 문서화).
- 예외·에러코드 같은 모듈 수준의 표현은 해당 모듈의 전용 패키지로 모아서 관리한다.
- 설정과 횡단 관심사는 별도 패키지로 분리해 책임을 명확히 한다.
- 테스트 코드는 대상의 패키지 구조를 그대로 반영하고, 테스트 전용 픽스처는 테스트 소스가 소유한다.

### 리포지토리 접근 범위

- 도메인 영속 포트는 Spring Data 인터페이스 자체다. 별도 포트 인터페이스를 만들지 않는다.
- Spring Data 인터페이스는 도메인 모듈의 `repository` 패키지에 평탄하게 둔다. `custom/` 하위 패키지를 만들지 않는다.
- 엔티티를 반환하는 조회 메서드는 도메인 모듈 경계 밖으로 노출하지 않는다. 경계를 넘는 조회는 Info로 변환한다.
- 앱의 리포지토리 직접 접근은 금지한다. 예외: 격리 구역은 읽기 전용으로 허용한다.
- 애그리거트는 불변식·생명주기 일관성 단위로 묶고, 루트를 진입점으로 한다.
- 리포지토리는 애그리거트 루트마다 하나 둔다. 자식 엔티티 전용 리포지토리는 성능 예외로만 둔다.
- 외부는 루트를 통해서만 내부 엔티티를 변경한다. 애그리거트 간 참조는 ID만 보관한다.
- 도메인 서비스의 한 트랜잭션은 하나의 애그리거트만 변경한다.
- 자식 엔티티 리포지토리의 성능 예외는 조회와 벌크에만 적용한다.
- 자식 엔티티 직접 조회는 부모 전체 로딩 OOM·N+1을 피하기 위해 리포지토리·QueryDSL로 직접 조회한다.
- 자식 리포지토리를 통한 벌크 `UPDATE`·`DELETE`·대용량 `INSERT`는 허용한다. 자식 단건 `INSERT`는 금지한다.
- 소프트삭제 엔티티는 base `JpaRepository` finder 직접 호출을 금지한다. 예외: `deletedAt`이 없는 엔티티는 그대로 사용한다.
- 활성-only 조회는 이름에 `DeletedAtIsNull`을 담는다. 삭제분 포함 조회는 `IncludingDeleted`를 붙인다.
- 파생 쿼리 메서드: 정적 단순 조건(등호, `And`/`Or`, 정렬, 활성 필터)에서 사용한다.
- `@Query`(JPQL): 파생 쿼리로는 이름이 비대하거나 파생 파서가 처리하지 못할 때 사용한다.
- QueryDSL 프래그먼트: 런타임 조건 조합, 커서 페이지네이션, 복잡 동적 조인, 타입 안전 프로젝션이 필요할 때 사용한다.
- 네이티브 SQL: 방언 종속이 불가피할 때만 최후수단으로 사용한다.
- QueryDSL 프래그먼트도 `repository` 패키지에 평탄하게 둔다.
- 메서드명 규칙은 [coding-conventions](coding-conventions.md)의 네이밍 규칙을 따른다.

### 경계 원칙

- 내부 반환: 서비스와 리포지토리 간에는 엔티티를 사용한다.
- 경계 반환: 모듈 밖으로 나가는 조회는 도메인 소유 `info/` record로 변환한다.
- 명령 결과: 명령은 ID 등 최소 결과만 반환한다.
- 페이지 변환: `Page<Entity>`는 경계에서 `Page<Info>`로 변환한다.
- OSIV off: `open-in-view: false`로 경계 밖 지연 로딩을 런타임에 차단한다.
- 예외: admin `infrastructure/query`, batch `infrastructure/reader`는 엔티티 의존을 허용한다.
- 예외: 격리 구역의 크로스 스키마 조회는 읽기 전용이다.
- 예외: admin·batch는 MSA 분리 대상에서 제외한다; 분리 시 read model로 대체한다.
- 외부 기능 포트: 소비 도메인의 `port/`가 소유한다.
- 기술 포트: 도메인 의미 없는 기술 포트는 common이 소유하고 infra가 구현한다.
- 포트 구현: 외부 기능 포트는 external이 구현한다.
- 포트 명명: 포트명은 벤더 중립이어야 한다.
- DIP 원칙: 인터페이스와 구현이 같은 모듈이면 DIP가 아니다.

### 트랜잭션 경계

- 쓰기 메서드(`Appender`·`Modifier`·`Remover`·`Processor`): 트랜잭션 경계를 소유하며 `@Transactional`을 적용한다.
- 취소 불가능한 부작용이 있는 쓰기: 부작용은 트랜잭션 밖에서 수행하고, 결과 영속만 프로그램적 `TransactionTemplate`으로 감싼다.
- 프로그램적 트랜잭션 사용 조건: 커밋 실패 시 부작용이 롤백되지 않는 경우에만 사용한다.
- 프로그램적 트랜잭션 호출 조건: 호출자는 반드시 트랜잭션 밖에서 호출한다.
- 프로그램적 트랜잭션 제한: 영속은 자기 애그리거트만 변경한다.
- facade: 트랜잭션을 열지 않고 도메인 서비스 조립·조율만 수행한다.
- 여러 도메인 변경: 동기 트랜잭션 대신 도메인 이벤트 기반 최종일관성을 사용한다.
- 도메인 이벤트: 발행 도메인 커밋 후 발행하고, 소비는 멱등하게 처리한다.
- 예외: 아웃박스·보상 등 코레오그래피 패턴 선택은 프로젝트 결정 사항이며 본 가이드 범위 밖이다.
- 조회(`Reader`): `@Transactional(readOnly = true)` 범위 안에서 Info 변환까지 완료한다.
- OSIV off: 엔티티 → Info 변환은 반드시 트랜잭션 안에서 완료한다.

### 빌드가 강제하는 불변식

- 아래 규칙은 리뷰 대상이 아니라 빌드 차단 규칙이다.
- 에이전트는 코드 생성 후 이 목록으로 자기검증한다.
- 채택 팀은 각 규칙에 대응하는 강제 장치 구현 여부를 검증한다.
- 강제 장치(컨벤션 플러그인, 아키텍처 테스트)는 빌드 하네스가 소유한다.
- 계층 의존 방향(모듈 지도): 컨벤션 플러그인(컴파일 시점)
- JPA 매핑 클래스의 모듈 밖 시그니처 노출 금지, 타입 위치 강제, apps의 리포지토리 직접 접근 금지, base `JpaRepository` finder 직접 호출 금지(소프트삭제 엔티티만): 아키텍처 테스트(테스트 시점)
- web 컨트롤러 핸들러의 `@Operation`, `@ApiResponse`, `@Parameter(description)`, request/response 타입과 컴포넌트의 `@Schema` 선언 필수: 아키텍처 테스트(테스트 시점)
- 예외: 에러 상태 집합의 정확성은 리뷰로 검증한다.
- web 컨트롤러 핸들러의 int·Integer `@RequestParam` 직접 선언 금지: 아키텍처 테스트(테스트 시점)
- 예외: 페이징 파라미터는 common-web `PaginationRequest` 사용.
- 어드민 표면은 admin 패키지, `*AdminController` 네이밍, 클래스 레벨 `@Admin`, `/api/v{n}/admin/` 프리픽스를 모두 충족해야 한다: 아키텍처 테스트(테스트 시점)
- 메서드 레벨 `@Admin` 사용 금지.
- 각 모듈 베이스 패키지의 `@NullMarked`, null 계약, 포맷, 정적 분석 준수: Spotless, NullAway, Error Prone
- 금지 의존성(Lombok, H2) 사용 금지: 컨벤션 플러그인
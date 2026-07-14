# 다음 작업 (우선순위별)

7개 도메인 구현이 끝난 시점의 남은 작업을 우선순위로 정리한다. 새 세션은 이 문서 → 규칙 문서(`docs/`) → 설계서(`DOMAIN-DESIGN.md`) → 자동 메모리(`commerce-implementation-progress.md`) 순으로 맥락을 잡는다.

## 현재 상태 (완료분)

- 공용: `common-core`(Money·UuidV7Generator·ErrorCode·BaseException), `common-jpa`(BaseTimeEntity·MoneyConverter·JpaAuditingConfig).
- 도메인 7개 전부: `member`·`product`·`stock`·`cart`·`coupon`·`order`·`payment`. 각각 entity·VO·enum·Info·repository·service·exception·Flyway + 단위 테스트 + Testcontainers 실 PostgreSQL 영속 슬라이스.
- `./gradlew build` 그린, 133 테스트 통과. `settings.gradle.kts`에 10개 모듈 등록.
- 각 도메인은 콜드 서브에이전트 리뷰를 거쳐 타당한 지적을 반영했다. 값 범위 거부 예외 정책은 Option C(설계 거부 목록에 있으면 도메인 예외 400, 호출자 보장 선행조건이면 `IllegalArgumentException`)로 확정해 `docs/coding-conventions.md`에 반영했다.
- 빌드 게이트 특성·복제 패턴·리뷰 로그의 상세는 메모리 `commerce-implementation-progress.md`에 있다. 남은 작업의 원본도 그 메모리에 기록돼 있다.

작업 방식은 지금까지와 동일: 규칙 문서를 지키고, 도메인/모듈 단위로 `./gradlew spotlessApply build`로 검증하며, 큰 단위 완료 후 콜드 서브에이전트 리뷰를 받고 타당한 것만 반영한다.

---

## P0 — 먼저 결정할 것 (app 계층을 막는 경계 정책)

### 1. 아키텍처 테스트의 엔티티 경계 규칙 정제 — 완료 ((a+) 채택)

- 결정: (a+). ArchUnit 규칙 대상을 `..entity..` 패키지 의존 → `@Entity`·`@MappedSuperclass` 애노테이션 클래스 의존으로 교체(String 오버로드 `annotatedWith("jakarta.persistence.Entity")` — app-api 테스트 클래스패스에 `jakarta.persistence-api`가 없어 Class 오버로드 불가). 값 타입(enum·record·`@Embeddable` VO)은 경계 계약(명령 입력·Info·포트)으로 통과 허용. `that()` 제외 목록은 유지. 반영: `ArchitectureTest.java`, `docs/architecture.md`(:79·:166), `docs/entity-persistence.md`(추출 시 @Embeddable 애노테이션 스트립 부채 한 줄).
- 근거: 규칙이 막는 실제 해악(LazyInit·dirty·API-엔티티 결합)은 `@Entity`에만 발생. JPA가 도메인 컨벤션에서 `implementation` 스코프라 @Embeddable가 넘어도 `jakarta.persistence`가 app 컴파일 클래스패스에 안 샌다. `Address`(@Embeddable)가 이미 `OrderAppender.place` 명령 입력으로 넘는 선례. 상세·기각안(b·c·d)은 메모리 `commerce-p0-boundary-rule-decision`.
- 검증 완료: app-api에 domain-order 임시 의존 + 메인 소스 픽스처로, 생 `Order`(@Entity) 참조는 규칙 실패(param+return 2건)·`OrderStatus`(enum)+`OrderInfo` 참조는 통과 확인 후 임시물 전량 원복. `./gradlew :module-apps:app-api:spotlessCheck :module-apps:app-api:test` 그린.
- 남음: 소프트삭제 base finder(`findById` 등) 직접 호출 금지 ArchUnit 규칙 배선(member 리뷰 지적, `docs/architecture.md` "빌드가 강제하는 불변식" 등재)은 이 결정과 독립이라 별도 커밋으로 미룸. 규칙의 비공허 강제는 app-api가 도메인을 의존하는 P2에서 활성화된다(지금은 스캔 대상 0 → 공허 통과).

---

## P1 — 지원 인프라 (app 계층의 전제)

### 2. external-payment (`PaymentGateway` stub 어댑터) — 완료

- 구현: `module-external/external-payment`, `convention.external-module` + `externalModule { targetDomain.set("domain-payment") }`. `com.commerce.external.payment.StubPaymentGateway`가 `PaymentGateway` 구현 — approve는 항상 성공 승인 + 거래 ID 발급, cancel은 취소 거래 ID 반환. 상태 미보관·실패 시뮬레이션 없음(동기 stub 기준선; 실패·보상 경로는 P2 파사드가 테스트 더블로 검증).
- 배선: `@Component` — P2의 `com.commerce` 컴포넌트스캔이 도메인 `@Service`와 함께 어댑터도 자동 포함(별도 @Bean 불필요). spring-context만 의존(JPA 불필요).
- 검증 완료: `StubPaymentGatewayTest`(승인 성공·거래 ID 유일·취소 ID 반환), `./gradlew build` 그린. external-module 컨벤션이 대상 도메인+common 외 프로젝트 의존을 차단함도 확인.

### 3. common-messaging (`MessagePublisher` 발행 포트)

- 목표: 벤더 중립 발행 포트. `convention.common-module` 적용. transport 구현은 infra(추후).
- 소비자: order의 `OrderPaid`(이미 정의됨) 커밋 후 발행.
- 설계: `docs/architecture.md`(common 배치·발행 포트), `DOMAIN-DESIGN.md` §도메인 이벤트 명세.

### 4. common-web (경계 공통)

- 목표: ProblemDetail 핸들러(`BaseException.getErrorCode()`의 `status()`→HTTP, 그 외 `IllegalArgumentException` 등은 500), 요청 DTO Bean Validation 승격처, 더블서밋 멱등 필터.
- 근거: Option C가 전제하는 "핸들러가 `BaseException`은 매핑, IAE는 500"을 실제 배선하는 곳. cart get-or-create 경합·체크아웃 더블서밋 방어(설계 범위 밖으로 미뤄둔 것)도 여기 멱등 필터가 담당.
- 설계: `docs/architecture.md`(common-web), `docs/coding-conventions.md`(ErrorCode 계약).

### 5. SchemaFlywayFactory (common-jpa) + `app-migration` 앱 — 완료

- 구현: `com.commerce.jpa.migration.SchemaFlywayFactory`(common-jpa) — 스키마별 Flyway 인스턴스(각자 `flyway_schema_history`). 모든 마이그레이션이 `V1__`이라 단일 Flyway로 로케이션을 합치면 버전 충돌 → 스키마별 실행이 필수. flyway는 common-jpa에 `compileOnly`(도메인 런타임에 전파 안 함), 소비자 app-migration이 런타임 제공.
- `app-migration`(`module-apps/app-migration`): 얇은 부트 앱. `ApplicationRunner`가 `SchemaFlywayFactory.migrateAll(dataSource)` 실행, 도메인은 `runtimeOnly`(마이그레이션 리소스·엔티티만). `spring.flyway.enabled=false`(Boot 기본 Flyway가 `db/migration`을 재귀 스캔해 7개 V1 충돌하는 것 차단).
- 검증 완료: `SchemaMigrationValidationTest`(Testcontainers) — `migrateAll` 후 7개 도메인 엔티티를 한 EMF로 `validate` 부팅해 전부 통과(Member·Product·Stock·Cart·Coupon·Order·Payment). `./gradlew build` 그린(전 모듈).
- 유의: 수동 EMF는 Boot 기본 물리 네이밍(`CamelCaseToUnderscoresNamingStrategy`)을 명시해야 파생 컬럼(created_at 등)이 DDL과 맞는다. app-migration 표준 기동(`ddl-auto=validate`)은 app-api가 도메인 엔티티를 스캔하는 P2에서 이뤄진다.

---

## P2 — app-api 계층 (크로스 도메인 정책)

설계의 핵심 흐름을 파사드가 조율한다. 파사드는 트랜잭션을 열지 않고 도메인 서비스를 조립하며, 각 서비스가 자기 트랜잭션을 소유한다(`docs/architecture.md` 트랜잭션 경계).

### 6. 파사드 (`facade`)

- 체크아웃(주문 생성→결제): `DOMAIN-DESIGN.md` §크로스 도메인(체크아웃 6단계). 검증(회원 자격·변형/상품/재고 주문가능·쿠폰) → 주문 PENDING 생성(사가 앵커) → 재고 차감 → 쿠폰 확정 → 결제(0원 PG 생략) → PAID + `OrderPaid` 발행. 실패 시 동기 보상.
- 취소·환불: §크로스 도메인(취소). 이중 가드(결제 취소 선행 → 주문 취소가 재고·쿠폰 복원 게이트).
- 상품 등록→첫 변형·재고 시딩: §크로스 도메인(상품 등록). HIDDEN → 변형 DISABLED → 재고 → enable → show.
- 회원 탈퇴 가드: §회원 탈퇴 가드. 미배송 PAID 주문 있으면 탈퇴 거부(파사드가 order 조회로 게이트).
- 전제: P1(external-payment·common-messaging), P0(파사드가 Info를 읽고 명령 입력을 구성).

### 7. 컨트롤러 + 요청/응답 DTO (`presentation/v1`)

- 목표: REST 엔드포인트, 요청 DTO(Bean Validation), 응답 DTO(Info→Response 변환은 앱이 소유). ID는 문자열로 전송.
- 전제: P1(common-web).

### 8. `OrderPaid` 리스너 (`event/listener`)

- 목표: `OrderPaid` 커밋 후 소비 → 장바구니 `removeItems`(멱등). `DOMAIN-DESIGN.md` §도메인 이벤트 명세.
- 전제: P1(common-messaging), 6(발행 배선).

### 9. 배선

- `JpaAuditingConfig` import, 이벤트 발행 배선, 컴포넌트 스캔 등. app 실행 앱 설정 불변식(`open-in-view: false`, `ddl-auto: validate`)은 이미 `application.yml`에 있음.

---

## P3 — 후속 (소비자 도입 시)

- 도메인 read 보강: `getCoupon`/`CouponInfo`(체크아웃이 minOrderAmount 필요), payment `getByOrderId`(취소 파사드), `OrderInfo`에 이행/취소 이력 필드(paidAt·cancelledAt·reason 등, 주문 상세 뷰 도입 시).
- 실 PG 이중청구 방어: 현재 PG 호출이 `@Transactional` 안이라 동기 stub 기준에선 무해하나 실 PG면 승인 후 롤백 시 재청구 위험. 멱등키·tx 밖 호출·리컨실 중 택. (payment 리뷰 m2)
- Testcontainers `org.testcontainers.containers.PostgreSQLContainer`(2.x에서 deprecated) → `org.testcontainers.postgresql.PostgreSQLContainer` 정리. 동작에는 문제없음.

---

## 명시적 범위 밖 (구현 안 함)

`DOMAIN-DESIGN.md` §명시적 범위 밖 참조: 배송 추적·택배 연동, 부분 취소/환불, 회원 주소록, 인증·로그인, 실 PG·웹훅·아웃박스, 쿠폰 선착순 한도, 발급 쿠폰 회수, 옵션 구조 정규화, 대표가 집계, admin/batch 앱 등.

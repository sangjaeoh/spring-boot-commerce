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
- 활성화됨(P2): app-api가 도메인을 의존해 두 규칙(엔티티 경계·앱→리포지토리 금지)이 비공허 강제로 돌아간다. 리포지토리 규칙 대상은 `com.commerce..repository..`로 좁혔다(위 P2 #9 배선 참조).
- 소프트삭제 base finder 직접 호출 금지 ArchUnit 규칙 — 완료(별도 커밋). `ArchitectureTest.softDeleteRepositoryBaseFindersAreNotCalledDirectly`: 리포지토리의 제네릭 상위 인터페이스(`JpaRepository<E, ID>`) 타입 인자가 `deletedAt` 필드를 가진 `@Entity`(Member·Product)면 그 리포지토리에 base finder 직접 호출을 금지한다. 감지는 임포트된 `com.commerce` 클래스만으로 한다(Spring Data 타입 계층 해석 비의존 — `isAssignableTo(Repository)` 회피). 소프트삭제 엔티티 집합이 채워져 있음은 가드 테스트 `softDeleteEntitiesAreDetected`가 회귀 방지한다(빈 집합이면 규칙이 공허 통과하는데 `failOnEmptyShould`가 `.that()` 없는 이 규칙에선 못 잡는다 — 콜드 리뷰 지적). 금지 대상은 콜드 리뷰 2인 지적으로 `findById`·`existsById`·`findAll`·`findAllById`·`count`·`getReferenceById`까지 확장(existsById·findAllById는 소프트삭제 리포에 이미 `...DeletedAtIsNull` 대체 쿼리가 있어 실제 위험 입증)하고 `docs/architecture.md`:131을 락스텝 갱신했다. deprecated 별칭 getOne·getById는 대체 getReferenceById가 금지 대상이라 제외. 비공허성 검증: 임시로 소프트삭제 리포에 `findById`/`existsById` 호출을 넣어 규칙 실패 확인 후 원복(ProductVariant의 `findAllById`는 비소프트삭제라 미검출로 판별력도 확인). `./gradlew build` 그린, 184 테스트.

---

## P1 — 지원 인프라 (app 계층의 전제)

### 2. external-payment (`PaymentGateway` stub 어댑터) — 완료

- 구현: `module-external/external-payment`, `convention.external-module` + `externalModule { targetDomain.set("domain-payment") }`. `com.commerce.external.payment.StubPaymentGateway`가 `PaymentGateway` 구현 — approve는 항상 성공 승인 + 거래 ID 발급, cancel은 취소 거래 ID 반환. 상태 미보관·실패 시뮬레이션 없음(동기 stub 기준선; 실패·보상 경로는 P2 파사드가 테스트 더블로 검증).
- 배선: `@Component` — P2의 `com.commerce` 컴포넌트스캔이 도메인 `@Service`와 함께 어댑터도 자동 포함(별도 @Bean 불필요). spring-context만 의존(JPA 불필요).
- 검증 완료: `StubPaymentGatewayTest`(승인 성공·거래 ID 유일·취소 ID 반환), `./gradlew build` 그린. external-module 컨벤션이 대상 도메인+common 외 프로젝트 의존을 차단함도 확인.

### 3. common-messaging (`MessagePublisher` 발행 포트) — 완료

- 구현: `module-common/common-messaging`, `convention.common-module`(프로젝트 의존 0 — 포트·마커가 common-core 타입도 참조 안 함). `com.commerce.messaging.publish.MessagePublisher`(벤더 중립 발행 포트, `void publish(DomainEvent)`) + `com.commerce.messaging.event.DomainEvent`(발행 페이로드 마커 인터페이스). 페이로드 타입은 마커(A) 채택 — `publish(Object)`(B)는 타입 중심 DDD에서 유일한 느슨한 경계라 기각.
- 배선: `domain-order`가 common-messaging 의존(`convention.domain-module` 화이트리스트에 이미 등재) + `OrderPaid implements DomainEvent`(마커 고아 방지·이벤트 계약 확립). transport 어댑터·아웃박스·멱등 소비 지원은 범위 밖(infra·추후). 실제 발행 호출 배선(markPaid 커밋 후 발행)·커밋 후 리스너는 P2 #6·#8·#9.
- 검증 완료: 테스트 없음(순수 인터페이스 — 실행할 행위 없음). `./gradlew :module-common:common-messaging:spotlessApply build` 그린, `./gradlew build` 전 모듈 그린(도메인 Testcontainers 슬라이스 포함).

### 4. common-web (경계 공통) — 완료

- 구현: `module-common/common-web`, `convention.common-module`(common-core만 의존; auth는 이번 범위 밖). 슬림 스프링 웹(`spring-webmvc`+`spring-orm`, 서블릿 API는 `compileOnly`)만 의존해 임베디드 서버 스타터를 안 끌어온다.
  - `error/ProblemDetailHandler`(`@RestControllerAdvice` extends `ResponseEntityExceptionHandler`): `BaseException`→`ErrorCode.status()`, `MethodArgumentNotValidException`→400+필드·global 오류(`code`/`errors`), `ObjectOptimisticLockingFailureException`→409(entity-persistence.md:107), 그 외→500. 표준 MVC 예외(405·415·본문 파싱)는 상위 클래스가 각 상태로 처리해 500 폴백이 안 삼킨다.
  - `idempotency/IdempotencyStore`(포트) + `InMemoryIdempotencyStore`(인메모리 구현) + `IdempotencyFilter`(`OncePerRequestFilter`). `Idempotency-Key` 헤더·unsafe 메서드 한정, 중복→problem+json 409. 저장소는 in-flight 락(넉넉한 TTL)과 완료 후 dedup 창(짧은 TTL)을 분리해, 요청이 창보다 오래 걸리거나 타임아웃 재시도가 와도 원본 처리 중엔 새로 획득 못 한다.
- 포크 결정(질문): 멱등 저장소 = 포트+인메모리 구현(Redis-ready seam), 웹 테스트 = 최소 부트 웹 컨텍스트(`@SpringBootTest`+`webAppContextSetup`으로 실제 필터 체인), auth = 이번 범위 제외(common-auth 미의존).
- 배선: 핸들러·필터가 스프링 스테레오타입(`@RestControllerAdvice`/`@Component`)이라 P2 `com.commerce` 컴포넌트스캔이 자동 포함한다(app-api 배선 자체는 P2). `convention.common-module`이 이미 common-web를 화이트리스트(common-core·common-auth)에 두고 있었다.
- 검증 완료: 15개 테스트(핸들러 6·필터 5·저장소 4) — 4매핑·프레임워크 4xx 비삼킴·잘못된 본문 400·멱등 중복 거부·64스레드 단일승자·in-flight 락 지속. `./gradlew build` 전 모듈 그린(총 153 테스트). 콜드 서브에이전트 2인 리뷰 반영: in-flight/창 TTL 분리(양쪽 독립 지적), 스레드 경합 테스트·global 검증 오류·409 content-type·blank 헤더·잘못된 본문 테스트 추가. param-level 검증 매핑·코드 문자열 중앙화는 소비처 없음·과설계로 기각.
- 남음: P2 app-api가 이 핸들러·필터를 컴포넌트스캔으로 배선하고, 요청 DTO(`presentation/v1`)에 Bean Validation 애노테이션을 붙인다.

### 5. SchemaFlywayFactory (common-jpa) + `app-migration` 앱 — 완료

- 구현: `com.commerce.jpa.migration.SchemaFlywayFactory`(common-jpa) — 스키마별 Flyway 인스턴스(각자 `flyway_schema_history`). 모든 마이그레이션이 `V1__`이라 단일 Flyway로 로케이션을 합치면 버전 충돌 → 스키마별 실행이 필수. flyway는 common-jpa에 `compileOnly`(도메인 런타임에 전파 안 함), 소비자 app-migration이 런타임 제공.
- `app-migration`(`module-apps/app-migration`): 얇은 부트 앱. `ApplicationRunner`가 `SchemaFlywayFactory.migrateAll(dataSource)` 실행, 도메인은 `runtimeOnly`(마이그레이션 리소스·엔티티만). `spring.flyway.enabled=false`(Boot 기본 Flyway가 `db/migration`을 재귀 스캔해 7개 V1 충돌하는 것 차단).
- 검증 완료: `MigrationApplicationTest`(Testcontainers) — 앱을 `@SpringBootTest`로 부팅해 실 배선(DataSource→`ApplicationRunner`→`migrateAll`)을 태우고, (1) 7개 스키마 대표 테이블 존재, (2) 전 도메인 엔티티(루트 7 + 자식 4)가 마이그레이션 DDL에 `validate` 통과를 확인. validate는 테이블·컬럼·타입 정합만 본다(인덱스·유니크·`@Version` 의미 검증은 도메인별 슬라이스 테스트 소유). `./gradlew build` 그린(전 모듈).
- 유의: 검증 EMF는 Boot 기본 물리 네이밍(`CamelCaseToUnderscoresNamingStrategy`)을 명시해야 파생 컬럼(created_at 등)이 DDL과 맞는다. app-migration 표준 기동(`ddl-auto=validate` 엔티티 검증)은 app-api가 도메인 엔티티를 스캔하는 P2에서 이뤄진다.

---

## P2 — app-api 계층 (크로스 도메인 정책)

설계의 핵심 흐름을 파사드가 조율한다. 파사드는 트랜잭션을 열지 않고 도메인 서비스를 조립하며, 각 서비스가 자기 트랜잭션을 소유한다(`docs/architecture.md` 트랜잭션 경계). 파사드 계층(#6·#8·#9)과 컨트롤러(#7)로 P2를 완성했다. `./gradlew build` 그린, 182 테스트.

### 포크 결정(질문)

- 범위: 파사드 4개 + 발행 배선 + 리스너 + 최소 도메인 read + 배선 + 경계 활성화를 한 단위로 완성. 컨트롤러/DTO는 다음 세션(실컨텍스트 MockMvc 하네스가 별도 관심사).
- 발행 구현 위치: `module-infra/infra-messaging` 신설(`InProcessMessagePublisher`가 `ApplicationEventPublisher`로 위임). 근거 `MessagePublisher` Javadoc "transport 구현은 infra"·external-payment 선례. 저장소 첫 infra 모듈.
- 도메인 read: 최소 추가·규칙은 도메인에. `Coupon.calculateDiscount` minOrderAmount 플로어(미달→0), `PaymentReader.getByOrderId` 신설, `OrderReader.hasUndeliveredPaidOrder` 신설.
- 검증 하네스: 실 PostgreSQL 통합 슬라이스(Testcontainers). 결제 실패는 declined 반환 테스트 더블, 재고·쿠폰 중간 실패는 spy 결함주입.

### 6. 파사드 (`facade`) — 완료

- 구현: `CheckoutFacade`·`OrderCancellationFacade`·`ProductRegistrationFacade`·`MemberWithdrawalFacade`. 전부 `@Component`, `@Transactional` 없음 — 각 도메인 쓰기가 자기 트랜잭션을 소유하고 파사드는 동기 보상만 한다.
- 체크아웃: 검증(회원 자격·주문가능 합성·쿠폰 적용성) → 주문 PENDING(사가 앵커) → 재고 차감 → 쿠폰 확정 → 결제(0원 PG 생략) → `markPaid`. 실패 지점별 보상(차감분만/전체 재고·쿠폰 복원 + 주문 취소). 체크아웃 보상은 §체크아웃 step5 순서(복원 후 취소 — 단독 소유라 정확히-1회), 사용자 취소는 §취소 순서(취소 선행이 복원 게이트).
- 크로스 도메인 거부는 `com.commerce.api.exception.ApiErrorCode`가 소유(단일 도메인에 속하지 않는 조율 규칙): MEMBER_NOT_ELIGIBLE·EMPTY_CART·NOT_ORDERABLE·INSUFFICIENT_STOCK·COUPON_NOT_APPLICABLE·PAYMENT_METHOD_REQUIRED·PAYMENT_DECLINED·ORDER_NOT_CANCELLABLE·WITHDRAWAL_BLOCKED. 변형·상품·재고 조회의 부재/삭제/미시딩은 예외를 잡아 NOT_ORDERABLE로 강등.
- 검증: 세 보상 분기(재고·쿠폰·결제) 전부 취소 호출·사유까지 확인. 결제 실패는 declined 게이트웨이, 재고·쿠폰 중간 실패는 `@MockitoSpyBean` 결함주입(`doThrow`)+`verify(cancel)`.

### 7. 컨트롤러 + 요청/응답 DTO (`presentation/v1`) — 완료

- 구현: `presentation/v1`에 컨트롤러 3개(`OrderController` 체크아웃+취소·`ProductController` 등록·`MemberController` 탈퇴)와 요청/응답 DTO(`request/`·`response/`). 파사드 4개에 얇게 위임(요청 DTO→도메인 입력, 결과 ID→응답 DTO). 엔드포인트: `POST /api/v1/orders`(201 orderId)·`POST /api/v1/orders/{id}/cancel`(204)·`POST /api/v1/products`(201 productId)·`DELETE /api/v1/members/{id}?reason=`(204).
- 포크 결정(질문): API 표면 = 파사드 4개 write 엔드포인트만(조회·단일도메인 쓰기는 P3 소비자 도입 시). ID는 원생 문자열이 아니라 타입된 입력(`UUID`·enum)으로 바인딩 — 잘못된 값이 프레임워크 400으로 깨끗이 떨어지고 도메인 선행조건이 버그 백스톱(500)으로 남게(`docs/coding-conventions.md`:30). 배선: common-web는 `runtimeOnly`(스캔 조립·코드 참조 없음), `@Valid` 트리거용 `spring-boot-starter-validation` 추가.
- 검증: 실컨텍스트 `@SpringBootTest`+`@AutoConfigureMockMvc`+Testcontainers 웹 하네스(`WebIntegrationTest`), 12 테스트. 두 경계 배선을 실제로 증명 — `API_EMPTY_CART` 등 problem+json `code`는 `ProblemDetailHandler` 컴포넌트스캔이, `DUPLICATE_REQUEST` 409는 멱등 필터 자동등록이 활성일 때만 통과(끊기면 500·201로 실패). 콜드 리뷰 2인 반영: 옵션 배열 null 원소를 `List<@NotNull OptionRequest>`로 400 강제(미수정 시 client→500, must-fix), 체크아웃 해피패스에 쿠폰·배송비 매핑 검증(discount·payAmount·issuedCouponId), 멱등키 랜덤화, 응답 `from` Javadoc·옵션 라벨·problem+json content-type 단언 보강.
- 하드윈(Boot 4.1): `@AutoConfigureMockMvc`가 `org.springframework.boot.webmvc.test.autoconfigure`로 이동(`spring-boot-starter-webmvc-test` 필요, `starter-test`에 미포함). Jackson 3(`tools.jackson.databind.ObjectMapper`) 단독 — Jackson 2 databind 미존재. `spring-boot-starter-web`은 hibernate-validator를 안 끌어와 `@Valid`엔 `starter-validation` 별도 필요. presentation 서브패키지는 `package-info.java` 불필요(NullAway `AnnotatedPackages=com.commerce` 접두 재귀).
- 남음(선택): app-api 웹 하네스와 파사드 하네스가 각자 Testcontainers 컨테이너를 띄운다(마이그레이션 2회). 공유 홀더로 1개로 줄일 수 있으나 기존 `FacadeIntegrationTest`를 건드려야 해 미룸(마이그레이션 테스트도 독립 컨테이너라 그룹별 패턴과 일관).

### 8. `OrderPaid` 리스너 (`event/listener`) — 완료

- 구현: `OrderPaidListener`가 `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`로 `cartModifier.removeItems`를 소비(멱등·실패 삼킴). 발행은 `OrderModifier.markPaid`가 트랜잭션 안에서 `OrderPaid`를 내고 in-process transport가 `ApplicationEventPublisher`로 재발행 → 커밋 후에만 통지(롤백 시 발행 없음).
- 하드윈: AFTER_COMMIT 단계에는 방금 커밋된 트랜잭션 동기화가 아직 살아 있어, `REQUIRED` 소비가 그 죽은 트랜잭션에 합류하면 delete가 재커밋되지 않아 유실된다. `REQUIRES_NEW`가 필수. 통합 테스트("체크아웃 후 장바구니 비움")가 이 유실을 잡았다.

### 9. 배선 — 완료

- `ApiApplication`: `@SpringBootApplication(scanBasePackages = "com.commerce")` + `@EntityScan("com.commerce")` + `@EnableJpaRepositories("com.commerce")` + `@Import(JpaAuditingConfig.class)`. 도메인 `@Service`·external·infra `@Component`를 전역 스캔으로 조립하고, 엔티티·리포지토리 스캔도 `com.commerce`로 넓혀야 도메인 매핑·저장소가 잡힌다.
- 경계 활성화: app-api가 도메인을 의존해 두 ArchUnit 규칙이 비공허해졌다. 엔티티 경계는 통과(파사드가 값 타입·Info만 참조). 리포지토리 규칙 대상을 `com.commerce..repository..`로 좁혔다 — Spring Data 자신의 `org.springframework.data.jpa.repository.config`(@EnableJpaRepositories)까지 `..repository..`가 잡던 것을 배제(비활성 시절엔 드러나지 않던 과대 매칭).
- 하드윈: `@EntityScan`은 Boot 4에서 `org.springframework.boot.persistence.autoconfigure`로 이동. app-api 통합 테스트는 정적 컨테이너+`SchemaFlywayFactory.migrateAll`을 컨텍스트 생성 전에 실행해 `ddl-auto=validate` 부팅을 태운다(롤백 없이 — 커밋 후 리스너가 실제로 돌게).

---

## P3 — 후속 (소비자 도입 시)

- 도메인 read 보강(남은 것): `OrderInfo`에 이행/취소 이력 필드(paidAt·cancelledAt·reason 등, 주문 상세 뷰 도입 시), 회원별 주문 목록 리더(주문 이력 엔드포인트·컨트롤러 #7 도입 시). P2에서 해소분: 체크아웃 minOrderAmount는 `Coupon.calculateDiscount` 플로어로, 취소 파사드의 payment 조회는 `PaymentReader.getByOrderId`로 처리했다(별도 `getCoupon`/`CouponInfo`는 불필요 — 쿠폰 적용성이 도메인에 남는다).
- 실 PG 이중청구 방어: 현재 PG 호출이 `@Transactional` 안이라 동기 stub 기준에선 무해하나 실 PG면 승인 후 롤백 시 재청구 위험. 멱등키·tx 밖 호출·리컨실 중 택. (payment 리뷰 m2)
- Testcontainers `org.testcontainers.containers.PostgreSQLContainer`(2.x에서 deprecated) → `org.testcontainers.postgresql.PostgreSQLContainer` 정리. 동작에는 문제없음.

---

## 명시적 범위 밖 (구현 안 함)

`DOMAIN-DESIGN.md` §명시적 범위 밖 참조: 배송 추적·택배 연동, 부분 취소/환불, 회원 주소록, 인증·로그인, 실 PG·웹훅·아웃박스, 쿠폰 선착순 한도, 발급 쿠폰 회수, 옵션 구조 정규화, 대표가 집계, admin/batch 앱 등.

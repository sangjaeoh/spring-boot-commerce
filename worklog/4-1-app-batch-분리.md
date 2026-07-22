# 작업 4-1: app-batch 분리

## 설계

### 현황

- 스케줄 잡 2개가 app-api에서 돈다: `PendingOrderSweepFacade.reconcileStalePending()`(미결 주문 스윕), `PaymentConfirmationFacade.reconcileStaleRequested()`(결제 리컨실 스윕).
- `SchedulingConfig`(@EnableScheduling·@EnableSchedulerLock)가 app-api `config`에 있다.
- ShedLock `LockProvider`는 infra-redis `SchedulerLockConfig`가 배선한다(이전 불필요, 주석만 갱신).
- `PaymentWebhookController`(app-api web/v1/payment)가 `PaymentConfirmationFacade.confirm(UUID)`을 공용한다 — REQUIREMENTS "웹훅 수신은 같은 확정 경로를 공유한다".
- `PendingOrderSweepFacade`도 종결 기록된 결제의 잔여를 `PaymentConfirmationFacade.confirm(UUID)`에 위임한다.
- 스윕이 `OrderModifier.markPaid`를 호출하면 `OrderPaid` 이벤트가 in-process 발행되고, app-api `OrderPaidListener`가 장바구니를 비운다.

### 해석 후보와 결정 — 웹훅의 거취

완료 기준 "app-api에 @EnableScheduling·스케줄러 관련 코드가 남지 않음"은 결제 리컨실 스윕(@Scheduled)도 이전을 요구한다. 그런데 확정 기계(confirm 경로 ~200줄: PG 조회 확정·잔여 종결·보상)는 웹훅(app-api)과 두 스윕(app-batch)이 공유하고, 앱 간 코드 공유 경로가 없다(모듈 지도에 앱 계층 공용 모듈 없음, 크로스 도메인 조율은 도메인·common에 둘 수 없음).

- 후보 A: 확정 기계를 두 앱에 복제. 기각 — 돈의 경로 복제는 드리프트 위험이 크고, 깊은 커버리지(리컨실 테스트)가 batch로 이전되면 app-api 사본은 얕게만 검증된다. REQUIREMENTS "같은 확정 경로를 공유한다"도 코드 수준에서 깨진다.
- 후보 B: 웹훅 표면(컨트롤러·request·에러코드)까지 app-batch로 이전. 채택 — 확정 기계 소유자가 하나가 되고, 모든 코드가 이동만으로 옮겨져 테스트가 그대로 통과한다. 웹훅은 PG의 비동기 종결 통지이므로 스윕과 같은 관할(사후 종결)이고, architecture.md app-batch 절이 "web은 배치 진입점이 필요할 때만 둔다"로 web 패키지를 허용한다.
- 후보 C: 웹훅을 기록만 하는 얇은 수신으로 바꾸고 확정은 스윕에 맡김. 기각 — 동기 확정이라는 관측 가능한 행동이 바뀌어 기존 웹훅 테스트가 깨진다(이전 작업 기준 위반).

트레이드오프(B): 웹훅 URL(`/api/v1/payments/webhook`)의 서빙 주체가 app-batch 배포로 바뀐다(게이트웨이 라우팅 변경 필요). app-batch가 web 서버·최소 SecurityConfig를 갖게 된다. 대가로 확정 기계 단일 소유·무복제를 얻는다.

### 가정

- 게이트웨이/방화벽 라우팅은 코드 밖 운영 사항이다(어드민 URL 네임스페이스와 같은 축). 코드에는 반영할 것이 없다.
- fake PG는 프로세스 내 메모리 상태다. app-api에서 청구된 거래를 app-batch 리컨실이 조회하면 NOT_FOUND가 난다 — 실 PG(외부 공유 시스템) 대역이므로 시뮬레이션 한계로 수용한다(이미 app-api 다중 인스턴스에서 같은 한계가 있고, REQUIREMENTS 134가 NOT_FOUND→실패 확정을 명시한다). 테스트는 한 컨텍스트 안이라 영향 없다.
- `OrderPaid` 소비(장바구니 비우기)는 발행 프로세스 안에서만 일어난다(in-process). 체크아웃(app-api)과 스윕·웹훅(app-batch) 모두 markPaid를 호출하므로 리스너는 두 앱에 각각 있어야 행동이 보존된다 → `OrderPaidListener`를 app-batch에 복제(39줄, 정합성 비필수 경로). 아웃박스 전환(작업 3)의 선행 관찰로 worklog에 남긴다.

### 이전 목록

app-batch 신설(`module-apps/app-batch`, `convention.app-module`, 베이스 패키지 `com.commerce.batch`):

- main 이동: `PendingOrderSweepFacade`, `PaymentConfirmationFacade` → `facade/`; `SchedulingConfig` → `config/`; `PaymentWebhookController`·`PaymentWebhookRequest` → `web/v1/payment/`.
- main 신설: `BatchApplication`, `package-info`, `config/ClockConfig`(app-api와 동일 배선, 앱 소유 설정이라 각자 가짐), `config/SecurityConfig`(무상태·webhook permitAll(HMAC은 컨트롤러가 검증)·actuator 허용·나머지 거부), `exception/BatchErrorCode`·`BatchException`(웹훅 2개 코드 이전 — 코드 문자열 "API_WEBHOOK_*"는 PG에 노출된 와이어 계약이라 값 유지), `event/listener/OrderPaidListener`(복제).
- resources: `application.yml` — app-api에서 `order.reconciliation.*`, `payment.reconciliation.*`, `payment.webhook.secret` 이전 + 공통 불변식(open-in-view false, ddl validate, graceful, ecs 로깅, health/metrics·probes).
- test 이동: `PendingOrderSweepTest`, `PendingOrderSweepStaleAfterGuardTest`, `PaymentReconciliationTest`, `PaymentReconciliationFaultTest`, `CompensationRestoreRetryTest`, `SchedulerLockTest`(facade), `PaymentWebhookControllerTest`(web/v1/payment).
- test 신설: `SharedPostgresContainer`·`SharedRedisContainer`(app-api 테스트 소유 하네스라 복제), `BatchIntegrationTest`(공통 하네스 — auth 시크릿·admin 시딩 불필요, webhook secret 필요). 웹훅 테스트는 `@AutoConfigureMockMvc`를 자체 선언.
- test 픽스처 치환: `ProductRegistrationFacade`(app-api 파사드)를 못 쓰므로 각 테스트의 `seedProduct` 헬퍼를 도메인 서비스 직접 호출(ProductAppender→ProductVariantAppender→StockAppender→ProductVariantModifier.enable→ProductModifier.show)로 치환. 그 외 픽스처는 도메인 서비스라 그대로.

app-batch build.gradle.kts:

- implementation: common-core·common-jpa·common-messaging, domain-order·payment·stock·coupon·cart, starter-web·starter-security·starter-data-jpa·starter-actuator, shedlock.spring, swagger 어노테이션(webhook 컨트롤러 OpenAPI 명세용 — springdoc UI는 요청 밖이라 미포함).
- runtimeOnly: common-web(스테레오타입 스캔 조립: ProblemDetail 핸들러·보안헤더·요청ID·멱등 필터), external-payment, infra-messaging, infra-redis, postgresql.
- testImplementation: starter-test, testcontainers(postgres·redis·junit), flyway(core·postgresql), domain-member·domain-product·domain-shared(픽스처 — main 코드는 shared 타입을 참조하지 않는다).

app-api 잔재 제거:

- 삭제: `SchedulingConfig`, 두 파사드, 웹훅 컨트롤러·request, `ApiErrorCode`의 WEBHOOK_* 2개 항목, `SecurityConfig`의 `PUBLIC_PAYMENT_PATHS`, build의 `shedlock.spring`, yml의 reconciliation·webhook 키, `WebIntegrationTest`의 `WEBHOOK_SECRET`·webhook secret 등록, `FacadeIntegrationTest`의 webhook secret 등록·ShedLock 언급 javadoc, yml lifecycle 주석의 @Scheduled 언급.

기타:

- settings.gradle에 `module-apps:app-batch` 등록.
- `ArchitectureTest.appBasePackagesAreDetected` 기대 집합에 `com.commerce.batch` 추가(의존은 모듈 목록에서 자동 파생).
- infra-redis 주석 "애노테이션 배선은 app-api 소유다" → app-batch로 갱신.

### 완료 기준(검증 가능한 테스트 목록)

1. 이전한 테스트(파사드·웹훅 통합 테스트 7파일)가 app-batch에서 전부 통과 — `:module-apps:app-batch:test`.
2. app-api에 `@EnableScheduling`·`@Scheduled`·`@SchedulerLock`·shedlock 의존·웹훅 표면이 남지 않음 — grep 0건 + `:module-apps:app-api:test` 통과(기존 스위트 회귀 없음).
3. 아키텍처 테스트 통과 — `:module-tests:test-architecture:test`(앱 베이스 패키지 감지에 batch 포함, 리포지토리 직접 접근·엔티티 경계·웹훅 컨트롤러 OpenAPI 규칙 포함).
4. `./gradlew build` 전체 성공.

## 설계 리뷰

AGENTS.md 작업 원칙·docs 규칙 대조 결과와 반영 내역:

- (반영) domain-shared를 implementation에서 뺐다 — batch main 코드는 shared 타입(Money 등)을 임포트하지 않으므로 컴파일 의존이 아니다. 런타임은 domain-order 등의 전이 의존이 공급하고, 테스트 픽스처만 testImplementation으로 쓴다. "의존 범위가 더 좁은 쪽" 규칙 정합.
- (반영) common-web은 runtimeOnly — app-api는 AuthUser 코드 참조 때문에 implementation이지만 batch는 코드 참조가 없다(스테레오타입 스캔 조립만).
- (확인) starter-security 포함 근거 명시 — common-web ProblemDetailHandler가 @ExceptionHandler(AccessDeniedException) 시그니처로 security 클래스를 요구한다. 자동 구성 기본 잠금 대신 명시적 SecurityConfig(웹훅·actuator permitAll, 나머지 거부)가 이중 게이트 규칙(architecture.md)과 정합.
- (확인) swagger-annotations는 implementation — common-web은 실행 앱이 런타임을 공급한다는 전제로 compileOnly지만, batch는 실행 앱 자신이라 공급자가 없다. 어노테이션 jar 하나로 런타임 로딩 엣지를 제거한다.
- (확인) 웹훅 이전은 범위 문구("스윕 잡·스케줄링 설정·락 이전")를 넘지만, 완료 기준(스케줄러 코드 0)과 무복제 원칙이 강제하는 종속 이동이다 — 과설계가 아니라 요청된 분리의 전이 폐쇄.
- (확인) OrderPaidListener·ClockConfig 복제는 앱 소유물의 성격상 불가피(각 앱이 자기 조립을 소유). 복제 총량 55줄 내외로 확정 기계 복제(후보 A, 200줄+드리프트)와 비교해 수용.
- (확인) 테스트 이동은 testing.md "통합 테스트는 해당 앱 모듈의 src/test가 소유한다"와 일치. 픽스처 치환(seedProduct)은 파사드가 하던 5단계 호출의 인라인 전개라 행동 동일.
- (확인) 신규 동작 없음 — 전 항목이 이동·배선이라 TDD 대상이 아니고 "전후로 테스트 통과" 기준을 적용한다.

### 결정 기록

- 웹훅 이전(후보 B 채택) — 근거는 위 해석 후보 절.
- `infrastructure/reader` 미도입 — 스윕이 도메인 Reader(OrderReader.findPendingBefore, PaymentReader.findRequestedBefore)로 충분해 격리 구역이 필요 없다(범위 문구 "필요하면 사용").
- 에러코드 문자열 유지 — 와이어 계약 보존. 열거형 이름은 앱 소유 규칙대로 `BatchErrorCode`.
- batch 전용 스캔 축소 없이 `scanBasePackages = "com.commerce"` 유지 — app-api와 동일 방식. 클래스패스(의존 목록)가 조립 범위를 결정하므로 충분히 좁다(common-auth엔 스테레오타입이 없어 JWT 빈이 생기지 않는다).

## 구현 기록 (설계와의 차이)

- 이동한 통합 테스트 2건(PaymentReconciliationTest·PaymentReconciliationFaultTest)이 app-api `CheckoutFacade`를 픽스처로 쓰고 있었다(설계 시 미발견). 잔여 상태(승인 커밋 후 중단, 고아 청구)를 도메인 서비스 직접 호출로 재현하도록 픽스처를 재작성했다 — 검증 대상(스윕 수렴·환불 완결)은 동일하고, 체크아웃측 크래시 보상은 app-api `CheckoutFaultCompensationTest`가 계속 소유한다.
- 테스트 클래스패스의 domain-product 이미지 서비스가 `ImageStore` 포트 구현을 요구해 `testRuntimeOnly(external-storage)`를 추가했다(컨텍스트 조립용).
- app-api `application-local.yml`의 webhook 시크릿 블록을 app-batch `application-local.yml`(신설)로 이전했다.
- Dockerfile·docker-compose(full 프로필)·README에 app-batch 실행 경로를 추가했다 — 분리 후에도 로컬 풀스택이 스윕·웹훅을 계속 제공해야 행동 보존이 완성된다. 배포 편의 옵션 추가가 아니라 이전의 전이 폐쇄로 판단했다. api 서비스의 `PAYMENT_WEBHOOK_SECRET` 고아 환경변수는 제거했다.
- 이전 테스트 수 정정: 28개(파사드 22 + 웹훅 6). 설계 단계의 34개는 `@TestConstructor`를 `@Test`로 오집계한 값이다.

## 코드 리뷰

- diff 전수 검토 — 바뀐 줄 전부가 이 작업(모듈 신설·이동·잔재 제거·픽스처 치환·실행 경로 반영)으로 추적된다.
- app-api 잔재 grep 0건: shedlock·@Scheduled·@EnableScheduling·SchedulerLock·webhook·reconcil·PendingOrderSweep·PaymentConfirmation.
- 이동 파일은 git mv(R 표기)로 이력을 보존하고, 수정은 패키지·임포트·픽스처로 한정했다.
- 고아 정리 확인: ApiErrorCode WEBHOOK_* 2건, SecurityConfig PUBLIC_PAYMENT_PATHS, WebIntegrationTest WEBHOOK_SECRET, FacadeIntegrationTest 시크릿 등록·ShedLock javadoc, yml 키·스윕 언급 주석, 빈 디렉터리, compose 고아 env.
- REQUIREMENTS.md 164행("미확정 결제 확정은 app-api 내 @Scheduled 리컨실 스윕이 담당")은 당시 결정 기록 문서라 수정하지 않는다(언급만 남김). 현행 규칙은 docs/architecture.md가 소유한다.
- 게이트: `:module-apps:app-batch:test` 28/28 통과, `:module-apps:app-api:test` 통과, `:module-tests:test-architecture:test` 통과, `./gradlew build` 성공, `docker compose config` 유효.

# 작업 4-2: app-admin 분리

## 설계

### 현황

- 어드민 표면: `web/v1/admin/**` 7슬라이스 11컨트롤러(category, coupon×2, inquiry, member, order, product×3, stock) + request/response 30여 개.
- 컨트롤러는 대부분 단일 도메인 서비스 직접 위임. 파사드는 2개만 쓴다: `ProductRegistrationFacade`(product admin), `OrderRefundFacade`(order admin — 환불·반품 승인).
- 비-어드민 슬라이스 DTO 참조 2건: `MemberResponse`(member admin), `OrderPageResponse`(order admin, → OrderResponse → OrderLineResponse·AddressResponse).
- `AdminSeedConfig`(관리자 기동 시딩), `web/auth/Admin`(@PreAuthorize hasRole(ADMIN) 메타), SecurityConfig의 `ADMIN_PATHS` hasRole 게이트, OpenApiConfig의 @Admin bearer 표기가 app-api에 있다.
- `OrderRefundFacade`가 `ApiErrorCode.ORDER_NOT_REFUNDABLE`·`ORDER_RETURN_NOT_REQUESTED`를 던진다(다른 사용처 없음).
- multipart 설정(app-api yml)은 이미지 업로드용인데 `MultipartFile` 사용처는 `ProductImageAdminController`뿐이다.
- 테스트 얽힘(양방향):
  - app-api 비-어드민 테스트가 어드민 표면을 픽스처로 쓴다 — 어드민 HTTP 13곳(suspend·ship·delivery-confirmation·coupon 생성·hide·상품 등록) + `ProductRegistrationFacade` 주입 14파일.
  - 어드민 테스트가 구매자 표면을 픽스처로 쓴다 — 체크아웃 HTTP·CheckoutFacade 5파일, 상품 상세 HTTP 1파일, 회원 탈퇴 HTTP 1파일, 로그인 HTTP 1파일(AdminSeedingTest).
  - `SecurityFilterChainTest`의 어드민 게이트 2건, `AdminSeedingTest` 전체가 어드민 관할이다.

### 해석 후보와 결정

1) 공유 응답 DTO — 규칙 "본인/공개 슬라이스 소유, 어드민 참조"는 한 앱 안의 규칙이고 앱 간 코드 참조는 불가능하다.
   - 후보 A: DTO를 common-web으로 승격. 기각 — 도메인 응답 형상은 앱 소유물이고 common은 도메인 지식을 두지 않는다(architecture.md).
   - 후보 B: app-admin이 자체 사본을 소유(필드 동일 → 와이어 계약 보존). 채택 — 앱 분리 후에는 각 앱이 자기 응답 표면을 소유하는 것이 규칙의 정신이다. 사본은 순수 record 매핑 ~250줄.
2) 파사드 2개 — 주 소비자가 어드민 컨트롤러뿐이므로 app-admin `facade/`로 이동(작업 1의 판단 기준과 동일: 파사드는 소비 앱이 소유).
3) 에러코드 — `AdminErrorCode`·`AdminException` 신설, ORDER_NOT_REFUNDABLE·ORDER_RETURN_NOT_REQUESTED 이동(코드 문자열 "API_..."는 클라이언트 와이어 계약이라 값 유지 — 작업 1 BatchErrorCode와 동일 규약).
4) `infrastructure/query` 미도입 — 전 컨트롤러가 단일 도메인 Reader 경유로 충분하다(범위 문구 "필요하면 도입").
5) 테스트 픽스처 — 앱 경계를 넘는 HTTP 픽스처는 도메인 서비스 직접 호출로 치환한다(작업 1에서 확립한 패턴).
   - app-api 쪽: 하네스(Web/Facade IntegrationTest)에 `seedOnSaleProduct` 헬퍼 신설, 어드민 HTTP 픽스처는 MemberModifier.suspend·OrderModifier.ship/confirmDelivery·CouponAppender.create·ProductModifier.hide 등으로 치환.
   - app-admin 쪽: 하네스에 상품 시딩·결제완료 주문 구성 헬퍼(place→deduct→markStockDeducted→payment request→PG approve→confirmApproval→markPaid) 신설, 체크아웃·탈퇴·로그인 HTTP 픽스처를 서비스 호출로 치환. `ProductRegistrationFacade`는 admin 소유가 되므로 어드민 테스트의 주입은 유지.
6) `adminBearer` 잔존 소비자 — 비밀 문의 열람(InquiryControllerTest, 공개 엔드포인트의 ADMIN 역할 분기)은 app-api에 남는 행동이다. 하네스 시딩 제거 후 테스트 로컬에서 `MemberAppender.registerAdmin` + `JwtTokenCodec.issue(role=ADMIN)`로 토큰을 만든다.
7) 시큐리티 매트릭스 — app-api `SecurityFilterChainTest`의 어드민 게이트 2건은 삭제하고, app-admin에 자체 체인 테스트(무토큰 401·구매자 403·관리자 통과·actuator 공개)를 둔다.

### 가정

- 로그인·토큰 발급은 app-api 소유로 남는다. app-admin은 같은 HS256 시크릿으로 검증만 한다(`auth.jwt.secret` 공유). 관리자 로그인 HTTP 왕복은 app-api 테스트가 커버하므로 admin 테스트는 자격증명 검증+코덱 발급으로 대체한다.
- 어드민 운영(환불·배송·정지 등)은 `OrderPaid`를 발행하지 않으므로 app-admin에 이벤트 리스너는 두지 않는다. 테스트 픽스처의 markPaid 발행은 리스너 부재 시 무해(무소비 이벤트).
- 이미지 업로드는 어드민 전용이므로 multipart 설정은 admin yml로 이동한다.

### 이전 목록

app-admin 신설(`module-apps/app-admin`, `convention.app-module`, 베이스 패키지 `com.commerce.admin`):

- main 이동: `web/v1/admin/**` 전부(패키지 `com.commerce.admin.web.v1.admin.*`), `facade/ProductRegistrationFacade`·`OrderRefundFacade`, `config/AdminSeedConfig`.
- main 신설: `AdminApplication`, `package-info`, `web/auth/Admin`(자체 마커 — @Authenticated·@Anonymous는 어드민 컨트롤러가 안 쓰므로 미신설), `config/SecurityConfig`(JWT 필터+엔트리포인트, /api/v1/admin/** hasRole(ADMIN), infra permitAll, 나머지 denyAll, @EnableMethodSecurity), `config/AuthConfig`·`ClockConfig`(코덱 조립), `config/OpenApiConfig`(@Admin bearer 표기만), `exception/AdminErrorCode`·`AdminException`, DTO 사본: `admin/member/response/MemberResponse`, `admin/order/response/{OrderPageResponse,OrderResponse,OrderLineResponse,AddressResponse}`.
- resources: application.yml(graceful·validate·OSIV off·multipart(이동)·hikari·ecs·management·springdoc 기본 비노출·auth.jwt), application-local.yml(로컬 접속·jwt·admin 시딩·springdoc on).
- test 이동: 어드민 컨트롤러 테스트 10파일, `AdminSeedingTest`, `ProductRegistrationFacadeTest`, `OrderRefundFacadeTest`.
- test 신설: SharedPostgres/RedisContainer 사본, `AdminWebIntegrationTest`(시딩 속성·adminBearer·bearer·상품 시딩·결제완료 주문 헬퍼), `SecurityFilterChainTest`(어드민 인가 매트릭스).

app-admin build.gradle.kts:

- implementation: common-core·common-jpa·common-auth(JwtTokenCodec 코드 참조)·common-web(JwtAuthenticationFilter·엔트리포인트 코드 참조), domain-shared(Money in DTO)·member·product·stock·coupon·order·payment·inquiry, starter-web·security·validation·data-jpa·actuator, springdoc(어드민 API 문서 — app-api와 같은 기본 비노출·local 노출).
- runtimeOnly: external-payment(환불 PG 취소), external-storage(이미지), infra-messaging(발행 transport), infra-redis(멱등 필터 저장소), postgresql.
- testImplementation: starter-test·webmvc-test, testcontainers 3종, flyway 2종. (domain 추가 불필요 — main이 이미 전부 가짐. cart도 불필요.)

app-api 잔재 제거:

- 삭제: `web/v1/admin/**`, `web/auth/Admin`, `AdminSeedConfig`, 두 파사드, ApiErrorCode 2건, SecurityConfig ADMIN_PATHS·hasRole, OpenApiConfig @Admin 분기, yml multipart 블록, local yml auth.admin 블록.
- 하네스: WebIntegrationTest에서 ADMIN 시딩(상수·속성·adminBearer·MemberCredentialValidator) 제거, 두 하네스에 `seedOnSaleProduct` 추가.
- 픽스처 치환: 파사드 주입 14파일 + 어드민 HTTP 6파일(Member·Order·Coupon·Product·Inquiry·SecurityFilterChain).

기타: settings 등록, ArchitectureTest(`ADMIN_ONLY` FQN → `com.commerce.admin.web.auth.Admin`, 앱 기대 집합에 `com.commerce.admin`), Dockerfile app-admin 타깃, compose admin 서비스(8082, AUTH_JWT_SECRET·시딩 env), README 실행 경로.

### 완료 기준(검증 가능한 테스트 목록)

1. 이전·신설 어드민 테스트가 app-admin에서 전부 통과 — `:module-apps:app-admin:test`.
2. app-api에 admin 표면(패키지·경로·시딩·@Admin·ADMIN_PATHS) 잔재 grep 0건 + `:module-apps:app-api:test` 회귀 없음.
3. 아키텍처 테스트 통과(어드민 표면 4중 규칙이 app-admin에서 활성) — `:module-tests:test-architecture:test`.
4. `./gradlew build` 전체 성공, `docker compose config` 유효.

## 설계 리뷰

AGENTS.md 작업 원칙·docs 규칙 대조:

- (확인) DTO 사본 채택(후보 B)은 중복이지만 앱 소유권 규칙과 와이어 계약 보존의 교집합이다. 필드 형상이 갈리기 시작하면 그때 어드민 전용 형상으로 발전시킨다(지금은 요청 밖).
- (확인) 파사드 이동은 "파사드는 소비 앱이 소유"의 직접 적용. app-api에 남기면 main 데드 코드가 된다.
- (확인) 픽스처의 서비스 직접 호출 치환은 작업 1에서 확립·검증된 패턴이고 testing.md "통합 테스트는 해당 앱 소유"와 정합.
- (확인) admin 앱 시큐리티는 app-api 체인의 어드민 부분만 남긴 축소 미러 — 이중 게이트(URL hasRole + 클래스 @Admin) 유지.
- (반영) @Authenticated·@Anonymous를 app-admin에 미신설 — 어드민 컨트롤러가 쓰지 않는 마커를 만들면 투기적 설계다.
- (반영) app-admin에 common-messaging implementation 미포함 — main 코드가 이벤트 타입을 참조하지 않는다(런타임은 domain-order 전이 의존이 공급).
- (확인) 검사 갱신은 ADMIN_ONLY FQN과 앱 기대 집합 2곳 — 나머지 어드민 규칙은 앱 파생(appSubtreePatterns)이라 자동 적용된다.

### 결정 기록

- infrastructure/query 미도입(전 컨트롤러 단일 도메인 Reader). 크로스 도메인 어드민 조회가 생기면 그때 도입한다.
- 어드민 문서 표면(springdoc)은 app-api와 동일 규약(기본 비노출·local 노출)으로 유지 — 분리 전에도 어드민 엔드포인트가 문서화되던 행동의 보존.
- multipart 설정은 유일 사용처(이미지 어드민)를 따라 admin yml로 이동.

## 구현 기록 (설계와의 차이)

- 공유 DTO가 설계 조사보다 1건 많았다 — `IssuedCouponPageResponse`가 참조하던 구매자 슬라이스 `IssuedCouponResponse`도 어드민 소유 사본으로 복제했다(총 6파일).
- 어드민 테스트의 구매자 표면 의존이 조사보다 넓었다. HTTP 픽스처(체크아웃·쿠폰 발급·회원 탈퇴·주문 취소·로그인)뿐 아니라 효과 검증 GET(주문 상세·상품 상세·카탈로그 필터·문의 목록·발급 쿠폰 목록)도 있었다. 전부 도메인 서비스/리더 검증으로 치환했다 — 공개 표면의 JSON 형상·상태 매핑은 app-api 테스트가 계속 소유하므로 전체 커버리지는 보존된다.
- 하네스 `placePaidOrder` 픽스처가 초기에 스냅샷 상품명을 하드코딩해 편집-스냅샷 테스트가 깨졌다 — ProductReader로 실제 상품명을 읽도록 수정.
- "숨긴 상품은 담긴 라인이 있어도 체크아웃 거부" 테스트는 검증 대상이 체크아웃(app-api) 행동이라 admin으로 이전하지 않고 app-api `CheckoutFacadeTest`에 파사드 레벨로 이전했다(어드민 HTTP 없이 `productModifier.hide` 픽스처). 유일 소유 시나리오라 삭제하면 커버리지가 유실되기 때문이다.
- 재입고-체크아웃 왕복 테스트(Stock admin)는 소진·재차감을 도메인 차감으로 재현하도록 축소했다 — 소진 시 체크아웃 409는 app-api가 소유한다.
- app-api ProductControllerTest의 `objectMapper`가 픽스처 치환으로 고아가 되어 제거했다.
- 하네스 이름은 설계의 "AdminWebIntegrationTest" 대신 app-api와 같은 미러 네이밍(`com.commerce.admin.web.v1.WebIntegrationTest`, `facade.FacadeIntegrationTest`)을 택했다 — 패키지 구조 미러 규칙과 정합.

## 코드 리뷰

- app-api admin 잔재 grep 0건: AdminSeedConfig·adminBearer·ADMIN_EMAIL·web.auth.Admin·/api/v1/admin·hasRole·AdminController·auth.admin (예외: InquiryControllerTest의 의도된 로컬 admin 토큰 헬퍼).
- app-admin에 `com.commerce.api` 참조 0건 — 앱 간 코드 참조 없음.
- 이동 파일은 git mv(R 표기)로 이력 보존. DTO 사본 6파일에는 출처·와이어 계약 주석을 달았다.
- 고아 정리: ApiErrorCode 2건, SecurityConfig ADMIN_PATHS, OpenApiConfig @Admin 분기, yml multipart·local auth.admin 블록, WebIntegrationTest 시딩 일체, shipRequestBody 등 픽스처 헬퍼·임포트.
- app-api SecurityConfig의 @EnableMethodSecurity는 유지(@Authenticated·@Anonymous 사용 중).
- architecture.md의 "본인/공개 슬라이스 소유, 어드민 참조" 규칙 문구는 한 앱 안의 규칙이라 수정하지 않는다(언급만). REQUIREMENTS.md도 결정 기록 문서라 미수정.
- 게이트: `:module-apps:app-admin:test` 134/134 통과, `:module-apps:app-api:test`·`:module-tests:test-architecture:test` 통과(ADMIN_ONLY FQN·앱 기대 집합 갱신 반영), `./gradlew build` 성공, `docker compose config` 유효.

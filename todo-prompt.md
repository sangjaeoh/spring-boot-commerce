# TODO 착수 프롬프트

`todo.md`의 각 슬라이스를 새 세션에서 착수할 때 그대로 붙여넣는 프롬프트다. 번호는 `todo.md`와 일치한다. 위에서부터 순서대로 진행한다(슬라이스는 순서 의존이다).

각 프롬프트는 자립적이도록 공통 규칙·설계 사실을 앞에 담는다(서브에이전트는 이 세션 대화를 보지 못한다). 아래 코드블록만 복사해 붙여넣으면 된다.

- 선행 결정은 확정돼 각 프롬프트에 `결정` 라인으로 반영돼 있다 — 되묻지 않고 그대로 진행한다.
- 한 슬라이스가 끝나면 `todo.md`에서 해당 항목을 완료 처리하고 다음 번호로 넘어간다.

---

### 1. 빌드 배선 + common-web 테스트 오토컨피그 배제 게이트

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 가정·해석을 밝히고 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

맥락: 이 저장소는 어드민·유저 인증을 자체 얇은 JWT 코드(common-auth JwtTokenCodec + common-web
AuthTokenFilter/AuthUserArgumentResolver/AdminOnlyInterceptor)로 구현해 왔고, 이를 Spring Security
(무상태 JWT)로 전환하는 중이다. 이 슬라이스는 그 전환의 준비 단계다 — 의존만 배선하고, security를
클래스패스에 올린 채로도 common-web 테스트가 green임을 코드 변경 없이 먼저 증명한다.

작업: security 의존 배선 + common-web 테스트 오토컨피그 배제 게이트.
- 선결 리스크: security-web/core가 common-web 테스트 클래스패스에 오르면 Boot 시큐리티 오토컨피그가
  TestWebApplication(@SpringBootApplication)에 기본 보안체인을 걸어 /test/* 가 전부 401이 되고
  ProblemDetailHandlerTest·IdempotencyFilterTest·RequestIdFilterTest·LoginRateLimitFilterTest·paging
  테스트가 무더기로 깨진다. 이 슬라이스에서 배제로 봉합한다. app-api에는 아직 security를 넣지 않는다
  (커스텀 SecurityFilterChain이 없어 기본 보안체인이 전 엔드포인트를 잠그므로 — 코어 전환 슬라이스와 함께).
- 완료 기준:
  1) gradle/libs.versions.toml에 spring-security-web·spring-security-core를 versionless 엔트리로 추가한다
     (기존 spring-security-crypto와 동일 패턴 — Spring Boot BOM이 버전 전파).
  2) module-common/common-web/build.gradle.kts에 compileOnly(spring-security-web, spring-security-core)
     + compileOnly(jackson-databind) + testImplementation(security)를 추가한다. common-web의 확립된
     관례(servlet-api·validation-api가 compileOnly, 런타임은 실행 앱 제공)를 따른다.
  3) common-web 테스트 앱(TestWebApplication)에서 Boot 시큐리티 오토컨피그를 배제한다
     — @SpringBootApplication(exclude={SecurityAutoConfiguration, UserDetailsServiceAutoConfiguration,
     SecurityFilterAutoConfiguration}) 또는 test application.yml의 spring.autoconfigure.exclude. Boot 4.1
     (Spring Security 7)의 실제 repackage된 FQN을 확인해 적용한다.
  4) production 코드·behavior 변경 없음. ./gradlew build green + common-web 기존 테스트 전량 green
     (= 배제가 동작하는 증거)를 확인한다.
- 완료 후 ./gradlew build 통과 + architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 2. 인증 코어 전환 — common-web Spring Security 컴포넌트 + app-api SecurityConfig

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 가정·해석을 밝히고 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 자체 인증 강제 기전을 Spring Security SecurityFilterChain(무상태 JWT)으로 원자적으로 교체한다.
현재 강제는 (a) AuthTokenFilter(@Component)가 Bearer 검증 후 AuthUser를 요청속성으로 부착,
(b) AuthUserArgumentResolver가 AuthUser 파라미터 선언을 인증 강제로 사용(없으면 401),
(c) AdminOnlyInterceptor가 @AdminOnly 핸들러를 401/403 게이트한다. common-web 컴포넌트와 app-api
SecurityConfig는 상호 의존이라(둘 중 하나만 있으면 인증이 깨짐) 한 슬라이스로 전환한다.

타깃 스택(중요): Boot 4.1.0 = Spring Security 7 / Spring Framework 7 / Jackson 3
(tools.jackson.databind.ObjectMapper). 람다 DSL·authorizeHttpRequests·requestMatchers·PathPattern
매처만 쓴다(WebSecurityConfigurerAdapter·antMatchers·and() 없음).

결정(2026-07-18 확정, 되묻지 않음):
- 토큰 포맷 불변: common-auth(JwtTokenCodec·AuthRole·TokenClaims) 무변경. HS256 단일 키, sub=회원ID·
  role 클레임. 기존 발급 토큰 그대로 통용.
- principal: AuthUser를 Spring Security principal로 재사용하고 PreAuthenticatedAuthenticationToken(
  authUser, null, authorities)으로 감싼다(신규 토큰 클래스 만들지 않음). authority는 ROLE_{role}
  (hasRole('ADMIN') = ROLE_ADMIN).
- 강제 축: 어드민은 URL /api/v1/admin/** → hasRole('ADMIN'). @AdminOnly는 마커로 유지한다 — 삭제하면
  ArchUnit 어드민 4마커 규칙과 OpenApiConfig 도출이 깨진다. 셀프서비스는 anyRequest().authenticated().
- 배치: 재사용 컴포넌트(JwtAuthenticationFilter·인증 진입점·접근거부 핸들러)는 common-web에 plain
  클래스로 둔다(@Component 금지 — 스캔이 타 앱에 security 런타임을 강제하지 않게). 체인 조립은 app-api
  SecurityConfig가 new로 소유한다.
- 무상태: SessionCreationPolicy.STATELESS, csrf.disable(), formLogin.disable(), httpBasic.disable().
  SecurityContextRepository 저장 없음. SecurityContext는 SecurityContextHolder.createEmptyContext()로
  새로 만들어 세팅(공유 컨텍스트 변이 금지).
- 공개 경로 완전 열거(permitAll): POST /api/v1/auth/login, POST /api/v1/members(가입),
  GET /api/v1/products·/products/{id}, POST /api/v1/payments/webhook, /actuator/**,
  /v3/api-docs·/v3/api-docs/**·/swagger-ui/**·/swagger-ui.html, /error(ERROR 재디스패치 재인가로
  미처리 에러가 401로 둔갑하는 것 차단). POST /api/v1/members(공개) vs /api/v1/members/me(인증)는
  HttpMethod 스코프로 분리한다.
- 에러 파리티: 인증 진입점(401)·접근거부 핸들러(403)는 @RestControllerAdvice 밖에서 응답하므로,
  WebErrorCode(UNAUTHENTICATED/FORBIDDEN)·주입 Jackson 3 ObjectMapper로 ProblemDetail을 직렬화하고
  status·application/problem+json·charset을 수동 지정한다(새 ObjectMapper new 금지 — 주입 빈 사용).
  하드 계약 기준은 $.code + content-type + status. 로그인 실패는 컨트롤러까지 도달해
  $.code=MEMBER_INVALID_CREDENTIALS(도메인 401)로 남긴다 — 진입점으로 흘리지 않는다.
- NullAway: 신규 com.commerce.web.* 는 SecurityContextHolder.getContext().getAuthentication()의
  @Nullable 반환을 null-체크한다.

완료 기준:
- common-web 신규(plain 클래스): JwtAuthenticationFilter(OncePerRequestFilter, fail-open, @Component
  아님), RestAuthenticationEntryPoint(401), RestAccessDeniedHandler(403).
- common-web 변경: AuthUserArgumentResolver 소스를 요청속성 → SecurityContextHolder. AuthUser.ATTRIBUTE
  상수 제거. @AdminOnly Javadoc 갱신(애노테이션 유지). AuthWebConfig에서 인터셉터 등록 제거(리졸버 등록
  유지).
- common-web 제거: AuthTokenFilter, AdminOnlyInterceptor. 내 변경이 만든 고아만 제거(무관 데드코드 금지).
- app-api 신규: config/SecurityConfig(@EnableWebSecurity, SecurityFilterChain 빈) — 위 결정의
  무상태·비활성화·진입점/핸들러 조립·addFilterBefore(new JwtAuthenticationFilter(jwtTokenCodec),
  UsernamePasswordAuthenticationFilter.class)·authorizeHttpRequests(공개 매트릭스 + admin hasRole +
  anyRequest().authenticated()).
- build: app-api implementation(spring-boot-starter-security) + testImplementation(spring-security-test).
- 무변경 확인: 모든 컨트롤러(AuthUser 파라미터·@AdminOnly), AuthController·AuthConfig·AdminSeedConfig·
  RateLimitConfig·OpenApiConfig, module-tests/test-architecture(ArchUnit).
- 테스트: AuthTokenFilterTest → JwtAuthenticationFilterTest 재작성(SecurityContext 세팅/미세팅),
  AuthUserArgumentResolverTest 재작성(SecurityContext 소스 + @AfterEach clearContext()),
  AdminOnlyInterceptorTest 삭제, common-web 테스트 하네스(TestWebController 등) 정합. 신규 app-api
  SecurityConfig 통합 테스트: 공개 경로 무토큰 통과, 인증 경로 무토큰 401($.code=UNAUTHENTICATED +
  application/problem+json + nosniff/no-store), /api/v1/admin/** buyer 403·admin 통과, POST /members
  공개 vs GET /members/me 인증. 진입점/접근거부 직렬화 단위 테스트($.code).
- 게이트 순서: ArchUnit green(무변경) → common-web 테스트 green → app-api 통합 테스트 전량 green(특히
  AuthControllerTest 도메인401·PaymentWebhookControllerTest·MemberControllerTest 429·모든 Admin
  컨트롤러 401/403) → 신규 SecurityConfig 테스트 green.
- 완료 후 ./gradlew build 통과 + architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 3. 문서 개정 — REQUIREMENTS 176 · architecture.md

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: Spring Security(무상태 JWT) 전환으로 뒤집힌 문서 서술을 실코드에 맞춘다(docs가 규칙을 소유하므로
전환의 마지막 단계). 코드는 이미 전환됐다(슬라이스 2) — 이 슬라이스는 문서만 정합한다.
- 문제: REQUIREMENTS.md:176이 "Spring Security 전면 도입 기각"을 명시한다. docs/architecture.md:114가
  common-web를 "인증·멱등·시큐리티 헤더·로그인 레이트리밋 필터"로, 어드민 강제를 인터셉터 기전으로
  서술한다. 이 서술들이 전환 후 코드와 어긋난다.
- 완료 기준:
  1) REQUIREMENTS.md:176 — "Spring Security 전면 도입 기각" → "무상태 JWT + Spring Security 도입"으로
     개정하고 근거 교체. :177(HS256 단일 키)·:178(웹훅 HMAC은 토큰 표면 밖)은 유지. :52–55(인증 표면 표:
     401/403 동작)는 동작 서술이라 유효 — "자체 토큰 검증 필터" 류 표현만 갱신.
  2) docs/architecture.md:114(common-web 설명) — "인증 필터"를 "Spring Security 필터체인+인증 진입점·
     접근거부 핸들러"로, 어드민 강제 기전을 "인터셉터 → SecurityFilterChain URL hasRole"로 갱신. :82
     (OpenApiConfig)·:75–78(어드민 마커 네임스페이스)는 유효.
  3) 이중소유 회피: 공개 경로 매트릭스는 SecurityConfig(코드)+통합테스트가 소유한다. docs에 전 경로를
     프로즈로 재나열하지 않고 참조만 한다(AGENTS "검사가 강제하는 규칙 본문은 프로즈 재서술 금지").
  4) AGENTS 편집 규약 준수(불릿·표·현재상태 서술, 이모지·강조·"중요" 라벨 금지).
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

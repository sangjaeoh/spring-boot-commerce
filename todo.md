# TODO — Spring Security 전환 (무상태 JWT)

출처: "어드민·유저 자체 인증 → Spring Security(무상태 JWT) 전환" 설계 + 독립 3인 리뷰(Spring Security 정합성 / 아키텍처·레포 제약 / 마이그레이션 안전성), 2026-07-18. 세 리뷰 모두 "조건부 채택"이며, 조건으로 지목된 블로킹 항목을 아래 선행 결정·완료 기준에 반영했다. 전환은 **컨트롤러·토큰 포맷·ArchUnit 무변경**을 유지하고 인증 강제 기전만 자체 필터/인터셉터에서 Spring Security `SecurityFilterChain`으로 교체한다.

## 작업 규칙

- 각 항목은 하나의 슬라이스다. AGENTS.md·docs/의 규칙을 로딩하고 준수한다.
- 구현 전 가정·해석을 밝히고 검증 기준(완료 기준)을 합의한 뒤, 슬라이스 머지 전 **구현과 분리된 독립 리뷰**를 수행한다.
- 완료 후 `./gradlew build` 게이트 통과 + `docs/architecture.md`의 "빌드가 강제하는 불변식" 목록으로 자기검증한다.
- 슬라이스는 **순서 의존**이다(위에서부터). 각 슬라이스는 머지 시점에 빌드 green을 유지한다 — 절반만 전환된 인증 상태로 머지하지 않는다.
- 선행 결정은 전부 확정됐다(아래 "선행 결정" 참조). 제목에 `[결정]`이 붙은 항목은 본문 `결정` 라인을 따르고 되묻지 않는다.
- 각 항목은 `상태: 대기 | 보류(결정 대기) | 완료` 마커를 가진다. 자동 루프·새 세션은 위에서부터 첫 `대기` 항목만 수행한다.
- 세션별 착수 프롬프트는 `todo-prompt.md`가 소유한다(번호 일치). 자동 진행 규약은 `loop-prompt.md`가 소유한다.

## 선행 결정 (2026-07-18 확정)

설계 초안을 3인 독립 리뷰에 넘겨 도출한 최종 설계의 확정 사항이다. 재검토가 필요하면 해당 항목 착수 전에 이 절을 갱신한다.

- **채택 방향**: 인증을 Spring Security(무상태 JWT)로 전환한다. `REQUIREMENTS.md:176`이 명시 기각했던 결정을 뒤집으며, 문서 개정(슬라이스 3)을 전환의 일부로 포함한다. JWT는 유지하고 세션은 무상태(STATELESS)로 간다.
- **타깃 스택**: Boot 4.1.0 = Spring Security 7 / Spring Framework 7 / Jackson 3(`tools.jackson.databind.ObjectMapper`). 람다 DSL·`authorizeHttpRequests`·`requestMatchers`·`PathPattern` 매처만 사용한다(`WebSecurityConfigurerAdapter`·`antMatchers`·`and()` 없음).
- **강제 축**: 어드민은 URL `/api/v1/admin/**` → `hasRole('ADMIN')`로 강제한다. `@AdminOnly`는 **마커로 유지**한다(ArchUnit 어드민 4마커 규칙·`OpenApiConfig`가 존재를 읽음 — 삭제하면 규칙이 깨지므로 삭제 금지). 셀프서비스는 `anyRequest().authenticated()`가 강제한다(규칙 누락 시 자동 보호 = fail-safe).
- **principal·토큰 래핑**: `AuthUser`를 Spring Security principal로 재사용하고 `PreAuthenticatedAuthenticationToken(authUser, null, authorities)`으로 감싼다(신규 토큰 클래스 만들지 않음). authority는 `ROLE_{role}`(`hasRole('ADMIN')` = `ROLE_ADMIN`). `AuthUserArgumentResolver`는 소스만 요청속성 → `SecurityContextHolder`로 바꾼다(컨트롤러 시그니처 불변).
- **컴포넌트 배치**: 재사용 컴포넌트(`JwtAuthenticationFilter`·인증 진입점·접근거부 핸들러)는 common-web에 **plain 클래스**로 둔다(`@Component` 금지 — 스캔이 타 앱에 security 런타임을 강제하지 않게). 체인 조립(공개 경로·규칙)은 app-api `SecurityConfig`가 `new`로 소유한다.
- **의존 스코프**: common-web는 `compileOnly(spring-security-web, spring-security-core)`(모듈 슬림 관례 — 런타임은 app-api가 제공). app-api는 `implementation(spring-boot-starter-security)`. 버전은 `libs.versions.toml`에 versionless 엔트리로 등록(Spring Boot BOM이 전파, `spring-security-crypto`와 동일 패턴).
- **토큰 포맷 불변**: `JwtTokenCodec`·`AuthRole`·`TokenClaims`(common-auth)는 무변경 — HS256 단일 키, `sub`=회원ID·`role` 클레임. 기존 발급 토큰은 전환 후 그대로 통용된다(데이터 마이그레이션·토큰 무효화 없음).
- **무상태·비활성화**: `SessionCreationPolicy.STATELESS`, `csrf.disable()`, `formLogin.disable()`, `httpBasic.disable()`. `SecurityContextRepository` 저장 없음(요청 스코프 스레드로컬로 충분). `SecurityContextHolder.createEmptyContext()`로 컨텍스트를 새로 만들어 세팅(공유 컨텍스트 변이 금지).
- **공개 경로 완전 열거**: permitAll = `POST /api/v1/auth/login`, `POST /api/v1/members`(가입), `GET /api/v1/products`·`/products/{id}`, `POST /api/v1/payments/webhook`, `/actuator/**`, `/v3/api-docs`·`/v3/api-docs/**`·`/swagger-ui/**`·`/swagger-ui.html`, **`/error`**(ERROR 재디스패치 재인가로 미처리 에러가 401로 둔갑하는 것 차단). `POST /api/v1/members`(공개) vs `/api/v1/members/me`(인증)는 HttpMethod 스코프로 분리한다.
- **에러 파리티**: 인증 진입점(401)·접근거부 핸들러(403)는 `@RestControllerAdvice` 밖에서 응답하므로, `WebErrorCode`(UNAUTHENTICATED/FORBIDDEN)·주입 Jackson 3 `ObjectMapper`로 `ProblemDetail`을 직렬화하고 status·`application/problem+json`·charset을 수동 지정한다. 하드 계약 기준은 `$.code` + content-type + status(기존 테스트가 검증하는 축). 로그인 실패는 컨트롤러까지 도달해 `$.code=MEMBER_INVALID_CREDENTIALS`(도메인 401)로 남긴다 — 진입점으로 흘리지 않는다.
- **NullAway**: 신규 `com.commerce.web.*`는 `SecurityContextHolder.getContext().getAuthentication()`의 `@Nullable` 반환을 null-체크한다.

---

## 슬라이스 (순서 고정: 1 → 2 → 3)

### 1. 빌드 배선 + common-web 테스트 오토컨피그 배제 게이트
- 상태: 대기
- 목표: security 의존을 배선하고, common-web 테스트가 security를 클래스패스에 올린 채로도 green임을 먼저 증명한다(코어 전환의 선결 리스크를 코드 변경 없이 격리 검증).
- 문제(선결 리스크): security-web/core가 common-web 테스트 클래스패스에 오르면 Boot 시큐리티 오토컨피그가 `TestWebApplication`(@SpringBootApplication)에 기본 보안체인을 걸어 `/test/*`가 전부 401이 되고, `ProblemDetailHandlerTest`·`IdempotencyFilterTest`·`RequestIdFilterTest`·`LoginRateLimitFilterTest`·paging 테스트가 무더기로 깨진다. app-api에 스타터-security를 먼저 넣으면 커스텀 `SecurityFilterChain`이 없어 기본 보안체인이 전 엔드포인트를 잠그므로, app-api 배선은 이 슬라이스에 넣지 않는다(코어 전환과 함께).
- 완료 기준:
  - `libs.versions.toml`에 `spring-security-web`·`spring-security-core` versionless 엔트리 추가(`spring-security-crypto` 패턴).
  - common-web `build.gradle.kts`에 `compileOnly(spring-security-web, spring-security-core)` + `compileOnly(jackson-databind)`(진입점/핸들러 직렬화용) + `testImplementation`(security, 신규 컴포넌트 테스트용) 추가.
  - common-web `TestWebApplication`에서 Boot 시큐리티 오토컨피그 배제(`@SpringBootApplication(exclude={SecurityAutoConfiguration, UserDetailsServiceAutoConfiguration, SecurityFilterAutoConfiguration})` 또는 test `application.yml`의 `spring.autoconfigure.exclude`). Boot 4.1의 실제 repackage FQN을 확인해 적용한다.
  - production 코드·behavior 변경 없음. `./gradlew build` green + **common-web 기존 테스트 전량 green**(= 배제가 동작하는 증거).
- 범위: 소.
- 이 항목을 1번에 두는 이유: 리뷰가 지목한 유일한 "숨은 빌드 파손" 리스크를 코어 전환 전에 격리해, 슬라이스 2가 red일 때 원인이 오토컨피그인지 전환 코드인지 헷갈리지 않게 한다.

### 2. 인증 코어 전환 — common-web Spring Security 컴포넌트 + app-api SecurityConfig [결정]
- 상태: 대기
- 결정(2026-07-18): 위 선행 결정 전부(채택 방향·타깃 스택·강제 축·principal·배치·무상태·공개 경로·에러 파리티). 되묻지 않는다.
- 문제: 현재 인증 강제는 (a) `AuthTokenFilter`(@Component)가 Bearer 검증 후 `AuthUser`를 요청속성으로 부착, (b) `AuthUserArgumentResolver`가 파라미터 선언을 인증 강제로 사용(없으면 401), (c) `AdminOnlyInterceptor`가 `@AdminOnly` 핸들러를 401/403 게이트한다. 이 자체 기전을 Spring Security `SecurityFilterChain`으로 교체한다. common-web 컴포넌트와 app-api `SecurityConfig`는 상호 의존(둘 중 하나만 있으면 인증이 깨짐)이라 한 슬라이스로 원자적으로 전환한다.
- 완료 기준:
  - common-web 신규(plain 클래스): `JwtAuthenticationFilter`(OncePerRequestFilter — Bearer 검증 성공 시 `createEmptyContext()`에 `PreAuthenticatedAuthenticationToken(authUser, null, [ROLE_{role}])`를 담아 `SecurityContextHolder.setContext(...)`; fail-open; `@Component` 아님), `RestAuthenticationEntryPoint`(401 problem+json `code=UNAUTHENTICATED`), `RestAccessDeniedHandler`(403 problem+json `code=FORBIDDEN`) — 둘 다 주입 `ObjectMapper`로 `ProblemDetail` 직렬화 + status/content-type/charset 수동 지정.
  - common-web 변경: `AuthUserArgumentResolver` 소스를 요청속성 → `SecurityContextHolder`(principal이 `AuthUser`면 반환, 아니면 `UnauthenticatedException`). `AuthUser.ATTRIBUTE` 상수 제거. `@AdminOnly` Javadoc을 "강제는 SecurityFilterChain URL 규칙"으로 갱신(애노테이션 자체는 유지). `AuthWebConfig`에서 인터셉터 등록 제거(리졸버 등록만 유지).
  - common-web 제거: `AuthTokenFilter`, `AdminOnlyInterceptor`. 이로 인해 고아가 된 것만 제거(무관 데드코드 금지).
  - app-api 신규: `config/SecurityConfig`(`@EnableWebSecurity`, `SecurityFilterChain` 빈) — STATELESS·csrf/formLogin/httpBasic disable·`exceptionHandling`(진입점·접근거부 핸들러 `new`로 조립)·`addFilterBefore(new JwtAuthenticationFilter(jwtTokenCodec), UsernamePasswordAuthenticationFilter.class)`·`authorizeHttpRequests`(선행 결정 "공개 경로 완전 열거" 매트릭스 + `/api/v1/admin/**` hasRole('ADMIN') + `anyRequest().authenticated()`).
  - app-api build: `implementation(spring-boot-starter-security)` + `testImplementation(spring-security-test)`.
  - 무변경 확인: common-auth(`JwtTokenCodec` 등), 모든 컨트롤러(`AuthUser` 파라미터·`@AdminOnly`), `AuthController`·`AuthConfig`·`AdminSeedConfig`·`RateLimitConfig`·`OpenApiConfig`, `module-tests/test-architecture`(ArchUnit).
  - 테스트: `AuthTokenFilterTest` → `JwtAuthenticationFilterTest` 재작성(SecurityContext 세팅/미세팅), `AuthUserArgumentResolverTest` 재작성(SecurityContext 소스 + `@AfterEach clearContext()`), `AdminOnlyInterceptorTest` 삭제, common-web 테스트 하네스(`TestWebController` 등) 정합. 신규 app-api `SecurityConfig` 통합 테스트: 공개 경로 무토큰 통과, 인증 경로 무토큰 401(`$.code=UNAUTHENTICATED`+`application/problem+json`+nosniff/no-store), `/api/v1/admin/**` buyer 403·admin 통과, `POST /members` 공개 vs `GET /members/me` 인증. 진입점/접근거부 직렬화 단위 테스트(`$.code`).
  - 게이트 순서: ArchUnit green(무변경) → common-web 테스트 green → app-api 통합 테스트 전량 green(특히 `AuthControllerTest` 도메인401·`PaymentWebhookControllerTest`·`MemberControllerTest` 429·모든 Admin 컨트롤러 401/403) → 신규 SecurityConfig 테스트 green.
- 범위: 중~대.
- 이 항목의 위험: `/error` permitAll 누락·`csrf.disable()` 누락·진입점/핸들러 파리티·공개 경로 누락이 리뷰가 지목한 실패 지점이다. 완료 기준에 전부 명시했다.

### 3. 문서 개정 — REQUIREMENTS 176 · architecture.md
- 상태: 대기
- 목표: 전환으로 뒤집힌 문서 결정을 실코드에 맞춘다(docs가 규칙을 소유하므로 전환의 마지막 단계).
- 문제: `REQUIREMENTS.md:176`이 "Spring Security 전면 도입 기각"을 명시한다. `docs/architecture.md:114`가 common-web를 "인증·멱등·시큐리티 헤더·로그인 레이트리밋 필터"로, 어드민 강제를 인터셉터 기전으로 서술한다. 이 서술들이 전환 후 코드와 어긋난다.
- 완료 기준:
  - `REQUIREMENTS.md:176` — "Spring Security 전면 도입 기각" → "무상태 JWT + Spring Security 도입"으로 개정하고 근거 교체. `:177`(HS256 단일 키)·`:178`(웹훅 HMAC은 토큰 표면 밖)은 유지. `:52–55`(인증 표면 표: 401/403 동작)는 동작 서술이라 유효 — "자체 토큰 검증 필터" 류 표현만 갱신.
  - `docs/architecture.md:114`(common-web 설명) — "인증 필터"를 "Spring Security 필터체인+인증 진입점·접근거부 핸들러"로, 어드민 강제 기전을 "인터셉터 → SecurityFilterChain URL hasRole"로 갱신. `:82`(OpenApiConfig)·`:75–78`(어드민 마커 네임스페이스)는 유효.
  - 이중소유 회피: 공개 경로 매트릭스는 `SecurityConfig`(코드)+통합테스트가 소유한다. docs에 전 경로를 프로즈로 재나열하지 않고 참조만 한다(AGENTS "검사가 강제하는 규칙 본문은 프로즈 재서술 금지").
  - AGENTS 편집 규약 준수(불릿·표·현재상태 서술, 이모지·강조·"중요" 라벨 금지).
- 범위: 소.

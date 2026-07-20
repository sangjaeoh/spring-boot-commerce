# TODO — 주석·Javadoc 규칙 코드 적용 (잔여: 어댑터 모듈 + app-api web)

출처: `docs/coding-conventions.md`의 "### 코드 주석·Javadoc". 규칙 정본은 그 문서가 소유하며, 아래 슬라이스는 규칙을 코드에 정합시키는 마이그레이션이다.

app-api 파사드·비파사드, 7개 도메인 모듈, module-common, app-migration은 정리를 마쳤다. 남은 것은 **어느 슬라이스의 스캔 대상에도 든 적 없는 어댑터 모듈 2개**와, **면제 판정으로 요약 의무만 건너뛴 채 타입 doc 축약을 받지 않은 app-api web 계층**이다.

## 이번 규칙의 요점

이 마이그레이션은 이전 규칙과 방향이 반대인 항목이 있다. 이전 규칙을 기억으로 적용하지 말고 정본 문서를 로딩한다.

| 축 | 이전 규칙 | 현재 규칙 |
|---|---|---|
| 메서드 요약 | 시그니처가 계약 전부면 생략 | 모든 메서드에 한 문장(면제 7종 제외) |
| 영속 필드 | 규정 없음 | 전부 한 문장. 그 값이 무엇인지만 |
| enum 상수 | 규정 없음 | 전부 한 문장. 상태 enum은 진입 조건 포함 |
| 타입 doc | 조감도(조율·불변식·정책)를 소유 | 그 타입이 무엇인지만. 전이 규칙·동시성·설계 근거 금지 |
| 주석 언어 | 규정 없음(관행) | 한국어 + 용어집 등재 용어만 + 타입 doc 최초 병기 |
| 라인 주석 | 비자명한 "왜"만 | 동일 + 조율 메서드 단계 번호 주석 허용 |

## 작업 규칙

- 각 항목은 하나의 슬라이스다. AGENTS.md·docs/의 규칙을 로딩하고 준수한다. 주석 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다.
- 변경은 **동작·시그니처 무변경**이다 — Javadoc·주석만 손댄다. 로직·제어흐름·공개 API 형태·테스트 기대값을 바꾸지 않는다.
- **삭제 규약**: 타입 doc·주석에서 설계 근거·정책 서술을 잘라내기 전에 `DOMAIN_MODEL.md`·`REQUIREMENTS.md`·해당 메서드 doc 중 어디가 그 내용을 소유하는지 확인하고 파일·행 근거를 보고에 적는다. 소유처가 없으면 지우지 말고 적절한 위치(메서드 doc·단계 주석)로 옮긴다.
- 완료 검증은 **독립 리뷰가 주 게이트**다. 주석 존재·내용은 빌드가 강제하지 않으므로(Error Prone Javadoc 검사는 경고, 게이트 아님 → code-quality.md), `./gradlew build` green은 회귀·포맷 확인용이지 규칙 준수 증명이 아니다.
- 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(AGENTS.md 외과적 편집). 발산한 지점만 고친다.
- 슬라이스는 서로 **독립적**이다(각자 Javadoc-only·빌드 green이라 절반 머지 위험이 없다). 위에서부터 진행한다.
- 각 항목은 `상태: 대기 | 보류(결정 대기) | 완료` 마커를 가진다. 자동 루프·새 세션은 위에서부터 첫 `대기` 항목만 수행한다.
- 세션별 착수 프롬프트는 `todo-prompt.md`가 소유한다(번호 일치). 자동 진행 규약은 `loop-prompt.md`가 소유한다.

## 참조 구현

이미 현재 규칙으로 정리된 코드다. 새 슬라이스는 이 톤에 맞춘다.

- `module-domains/domain-order` 전체 — 엔티티 필드·enum 상수·서비스·Info의 기준.
- `module-apps/app-api/.../facade/` 전체 — 조율 단계 번호 주석과 타입 doc 축약의 기준. 특히 `CheckoutFacade.checkout()`의 8단계 번호 주석.
- 정적 팩토리 요약의 기준: `OrderInfo.from`·`CartInfo.from`·`PaginationResponse.from` 등 13곳이 전부 한 문장 요약을 가진다.

---

## 슬라이스

### 1. module-external + module-infra (어댑터 모듈)

- 상태: 대기
- 목표: 어느 슬라이스에도 든 적 없는 어댑터 모듈 2개에 요약 의무를 채우고 타입 doc을 "무엇인지"로 축약한다.
- **왜 남았나**: 기존 슬라이스 목록이 app-api·module-domains·module-common·app-migration만으로 갈려, 이 두 모듈은 스캔 대상에 한 번도 등장하지 않았다. 주석이 1차 규칙 시절 그대로다.
- 스캔 대상(9파일 전수):
  - `module-external/external-payment` — `FakePaymentGateway`, `FakeGatewayTimeoutException`, `package-info`
  - `module-infra/infra-messaging` — `InProcessMessagePublisher`, `package-info`
  - `module-infra/infra-redis` — `RedisIdempotencyStore`, `RedisLoginRateLimitStore`, `SchedulerLockConfig`, `package-info`
- 관측된 발산(포함하되 여기서 멈추지 말 것). 괄호는 확인된 소유처다:
  - `FakePaymentGateway` 타입 doc — 트리거 금액 분기 3종 `<ul>`, 인메모리 보관의 재시작 한계 수용. (`REQUIREMENTS.md:153,159,163`, `DOMAIN_MODEL.md:554`)
  - `RedisIdempotencyStore` 타입 doc — `SET NX` 원자 선점, TTL 만료, fail-closed 채택 근거. (`REQUIREMENTS.md:172`)
  - `RedisLoginRateLimitStore` 타입 doc — `INCR`+`PEXPIRE`를 한 Lua로 묶은 근거, fail-closed. (`REQUIREMENTS.md:173`)
  - `SchedulerLockConfig` 타입 doc — `SET NX` 선점·TTL 메커니즘. 마지막 줄이 "Redis 채택 근거는 REQUIREMENTS.md가 소유한다"고 스스로 밝히면서 앞 2줄을 더 쓴다. (`REQUIREMENTS.md:166`)
  - `InProcessMessagePublisher` 타입 doc — `AFTER_COMMIT` 통지 시점, 무손실·내구성 보장 없음. (`REQUIREMENTS.md:6`, `DOMAIN_MODEL.md:11`)
  - `RedisIdempotencyStore`의 상수 라인 주석 3건 — in-flight 수명 선택 근거는 국소적 "왜"라 남길 후보이나, `KEY_PREFIX` 주석은 코드가 말하는 What이다.
- 판정 지침: 이 모듈들의 오버라이드 구현(`approve`·`cancel`·`inquire`·`publish`·`tryBegin`·`complete`·`incrementAndCount`)은 상위 포트 계약을 그대로 따르므로 요약 면제다. 면제 확인을 위해 상위 포트(`PaymentGateway`·`MessagePublisher`·`IdempotencyStore`·`LoginRateLimitStore`) doc이 계약을 담고 있는지 본다 — 담지 못하면 상위를 고치는 편이 맞고, 그 판단 결과를 보고한다.
- 완료 기준: 공통(아래 "슬라이스 공통 완료 기준").
- 범위: 소.

### 2. app-api web 계층 (컨트롤러 + web DTO + auth 마커)

- 상태: 대기
- 목표: 애노테이션 면제를 요약 의무에만 적용하고, 면제가 걸리지 않는 축(타입 doc 축약·정적 팩토리 요약·협력자 계약 되풀이)을 정리한다.
- **왜 남았나**: 이전 슬라이스가 컨트롤러 15개(`@Operation`)와 web DTO 49개(`@Schema`)를 통째로 "면제 대상 = 스캔만"으로 두었다. 면제는 규칙의 "요약을 두지 않는 자리" 항목이지 타입 doc 금지 사항·정적 팩토리까지 면제하지 않는다.
- 스캔 대상(67파일 전수): `module-apps/app-api/src/main/java/com/commerce/api/web/` 하위 전부.
  - `web/auth/` 3파일 — 이미 현재 규칙으로 정리됐다(`06ac2dc`). 확인만 하고 규칙에 맞으면 손대지 않는다.
  - `web/v1/` 64파일 — 컨트롤러 15, request/response record 49.
- 면제의 정확한 경계(이 슬라이스의 핵심 판정):
  - `@Operation`·`@Schema`가 소유하는 것은 **API 계약**이다. 핸들러 메서드 요약, record 컴포넌트의 `@param`이 여기 걸린다 — 여기에 요약을 새로 다는 것은 규칙 위반이다.
  - 면제가 걸리지 않는 자리: 타입 doc의 금지 사항(전이 규칙·동시성 메커니즘·설계 근거), 컨트롤러의 private 헬퍼, request DTO의 `toXxx()` 변환 메서드, response DTO의 `from(XInfo)` 정적 팩토리.
- **정적 팩토리 판정(확정)**: response DTO의 `from(...)` 27곳에 요약을 단다.
  - 근거 1 — 규칙이 생성자 면제 사유를 "타입 doc·정적 팩토리 doc. 생성 계약은 팩토리가 소유한다"로 적는다. 팩토리는 면제의 반대편, 곧 계약의 소유처다.
  - 근거 2 — 완료된 슬라이스가 도메인 Info 12곳과 `PaginationResponse.from`에 전부 요약을 달았다. web response DTO만 비어 있는 것이 발산이다.
  - 톤은 참조 구현을 따른다: `/** 주문 엔티티에서 조회 모델을 만든다. */` → web에서는 `/** 주문 조회 모델에서 응답을 만든다. */` 수준의 한 문장.
- 관측된 발산(포함하되 여기서 멈추지 말 것):
  - `PaymentWebhookController` 타입 doc 7줄 — HMAC-SHA256 서명 검증 방식·상수 시간 비교·401 거부·중복 전달 무해가 전부 문서 소유다(`REQUIREMENTS.md:55,135,168`, `DOMAIN_MODEL.md:659`). 이전 계획(`plan.md`)이 1차 규칙 아래 "무변경"으로 판정했으나, 타입 doc 축약 규칙으로 재판정 대상이다.
  - 컨트롤러 타입 doc의 `<p>` 정책 서술 6건 — `CouponAdminController`(중지 비소급), `MemberAdminController`·`MemberController`(정지·탈퇴 독립 축), `ProductAdminController`(편집 스냅샷 무영향·논리삭제 비연쇄), `ProductVariantAdminController`(가격 변경 스냅샷 무영향), `StockAdminController`(변형당 1행), `ProductController`(전부 공개). 각각 `DOMAIN_MODEL.md`·`REQUIREMENTS.md` 소유 여부를 확인해 개별 판정한다 — 일괄 삭제도 일괄 존치도 아니다.
  - DTO 타입 doc이 협력자 계약을 되풀이하는 건 — `LoginRequest`(401 코드·계정 존재 비노출), `MemberRegistrationRequest`(도메인 에러코드 2종), `AddCartItemRequest`·`ChangeCartItemQuantityRequest`·`CheckoutRequest`(토큰 주체 도출 = 컨트롤러의 인증 계약), `DiscountRequest`(형별 조합 검증은 도메인 소유).
  - `PaymentWebhookController`의 private 헬퍼 3개(`requireValidSignature`·`sign`·`parse`) — 요약 없음. 면제가 걸리지 않는 자리다.
- 이 슬라이스에서 건드리지 말 것: 용어 발산(`발급 쿠폰`·`논리삭제`)은 슬라이스 3의 결정 대기 대상이다. 표현을 바꾸지 말고 관측만 보고한다.
- 완료 기준: 공통 + 아래.
  - `@Operation`·`@Schema`가 소유하는 자리에 요약을 새로 단 곳 0.
  - `from(...)` 27곳·`toXxx()` 6곳·private 헬퍼 3곳에 요약 누락 0.
  - 컨트롤러·DTO 타입 doc의 잔존 서술마다 "애노테이션이 담지 못하는 자기 계약"인 근거 또는 삭제 소유처가 보고에 있다.
- 범위: 중.

### 3. 소프트삭제·발급분 용어 전역 정합

- 상태: 보류(결정 대기)
- 대기 중인 결정: 아래 두 안 중 하나를 확정해야 착수할 수 있다. 용어를 고르는 일이라 코드에서 답이 나오지 않고, 어느 쪽을 고르든 이미 머지된 슬라이스의 표현을 바꾸므로 임의로 정하지 않았다.
- 목표: `soft delete`·`issued coupon`의 한국어 표기를 하나로 모은다.
- 발견 1: `DOMAIN_MODEL.md` 용어집은 `soft delete → 소프트삭제`로 등재하는데, 자바 소스는 `논리삭제`만 쓰고 `소프트삭제`는 0회다(domain-member 4곳·domain-product 3곳·app-api 4곳). `DOMAIN_MODEL.md:135` 자체가 한 줄에서 두 표기를 섞어 쓴다.
- 발견 2: 용어집은 `issued coupon → 발급분`인데 app-api Swagger `@Tag`·`@Schema`·`@Operation` 15곳 이상이 "발급 쿠폰"을 쓴다. domain-coupon 안쪽은 이미 "발급분"으로 맞췄으므로 남은 발산은 app-api 표면이다.
- 선행 결정 필요 (둘 중 하나, 두 용어에 같은 방향 적용):
  - **안 A — 용어집을 따른다**: 코드의 `논리삭제` 11곳을 `소프트삭제`로, `발급 쿠폰` 15곳 이상을 `발급분`으로 바꾼다. 용어집이 단일 출처라는 원칙에 맞고 `docs/`(architecture·entity-persistence)와도 일치하지만, 변경 지점이 많고 그중 다수가 `@Operation`·`@Schema`의 API 문서 문구다.
  - **안 B — 코드를 따른다**: 용어집 행을 `논리삭제`·`발급 쿠폰`으로 고치고 `DOMAIN_MODEL.md:135`의 혼용만 정리한다. 변경이 작지만 `docs/`의 기존 용례와 어긋나 그쪽도 함께 봐야 한다.
- 범위: 소.

---

## 슬라이스 공통 완료 기준

각 슬라이스는 스캔 대상 전 파일을 전수 스캔하고 아래를 만족한다.

- 요약 의무 4종(타입·메서드·영속 필드·enum 상수) 누락 0.
- 면제 7종(오버라이드·필드 반환 접근자·생성자·파생 쿼리 메서드·테스트 메서드·설명 문자열 보유 enum·애노테이션 소유 타입/멤버)에 요약을 단 곳 0.
- 타입 doc에 상태 전이 규칙·동시성 메커니즘·설계 근거 잔존 0.
- 필드 doc에 그 값이 게이트하는 동작·전이 효과 잔존 0.
- 형식 준수: 타입·메서드 요약은 3인칭 서술형, 필드·enum 상수 요약은 명사구.
- 주석 용어가 `DOMAIN_MODEL.md` 도메인 용어집과 일치. 미등재 개념을 썼으면 용어집에 등재하는 커밋을 포함한다.
- 삭제한 설계 근거는 소유처(`DOMAIN_MODEL.md`·`REQUIREMENTS.md`·메서드 doc)를 확인하고 파일·행 근거를 보고에 적는다. 소유처 확인 없이 삭제한 건 0.
- 동작·시그니처 무변경. `./gradlew build` green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 위 항목별 잔여 0을 전수 확인한다.

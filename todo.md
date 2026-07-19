# TODO — 주석·Javadoc 규칙 코드 적용

출처: `docs/coding-conventions.md`의 "### 코드 주석·Javadoc" 재정립(청중·정보격차 비대칭 축, 2026-07-19, main 머지 `5492de0`)을 실코드에 적용한다. 규칙 정본은 그 문서가 소유하며, 아래 슬라이스는 규칙을 코드에 정합시키는 마이그레이션이다. 대부분의 코드는 이미 규칙을 따르므로, 이 마이그레이션은 규칙에서 **발산한 지점만** 외과적으로 고친다(전면 재작성 아님).

## 작업 규칙

- 각 항목은 하나의 슬라이스다. AGENTS.md·docs/의 규칙을 로딩하고 준수한다. 주석 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다.
- 변경은 **동작·시그니처 무변경**이다 — Javadoc·주석만 손댄다. 로직·제어흐름·공개 API 형태·테스트 기대값을 바꾸지 않는다.
- 완료 검증은 **독립 리뷰가 주 게이트**다. 주석 존재·내용은 빌드가 강제하지 않으므로(Error Prone Javadoc 검사는 경고, 게이트 아님 → code-quality.md), `./gradlew build` green은 회귀·포맷 확인용이지 규칙 준수 증명이 아니다. 독립 리뷰가 "해당 카테고리 발산 0"을 확인해야 완료다.
- 구현 전 스캔 범위·판정 기준을 밝히고, 슬라이스 머지 전 **구현과 분리된 독립 리뷰**를 수행한다.
- 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(AGENTS.md 외과적 편집). 발산한 지점만 고친다.
- 슬라이스는 서로 **독립적**이다(각자 Javadoc-only·빌드 green이라 절반 머지 위험이 없다). 다만 3(잔여 sweep)은 1·2가 처리한 뒤 남은 것만 다루므로 마지막에 둔다.
- 각 항목은 `상태: 대기 | 보류(결정 대기) | 완료` 마커를 가진다. 자동 루프·새 세션은 위에서부터 첫 `대기` 항목만 수행한다.
- 세션별 착수 프롬프트는 `todo-prompt.md`가 소유한다(번호 일치). 자동 진행 규약은 `loop-prompt.md`가 소유한다.

## 적용 규칙과 관측된 발산 (정본: docs/coding-conventions.md "### 코드 주석·Javadoc")

2026-07-19 감사에서 관측된 발산이다. 구현자는 관측 예시에 그치지 말고 코드베이스 전반을 전수 스캔한다.

- `@throws` 파리티 — 정상 호출자가 마주칠 수 있는 전파 예외(도메인 예외 = `BaseException` 하위 거부·낙관락 충돌)는 `@throws`로 적고, 호출자 보장 선행조건의 `IllegalArgumentException`(버그 백스톱)은 적지 않는다. 관측 누락: `Stock.markSoldOut`·`markSellable`·`discontinue`, `StockModifier`의 위임 메서드, `Payment.approve`·`fail`. 관측 과문서화: `Money.minus`의 `@throws IllegalArgumentException`.
- Swagger 단일 출처 — web Request/Response DTO(record)의 계약은 `@Schema`/`@Operation`이 소유하고, 중복 Javadoc은 제거한다. 관측 중복: `CheckoutRequest`가 클래스 Javadoc + 클래스 `@Schema`를 둘 다 둠. 도메인 `Info`↔web `Response` 근접 중복(`OrderInfo`↔`OrderResponse`)은 각 타입이 자기 계층 관점만 서술하게 정리.
- 자기서술 요약 — 공개 메서드는 이름·반환 타입 외에 전파 예외·부수효과·경계동작이 없으면 요약을 생략하고, 이름을 되풀이할 뿐인 요약은 두지 않는다.
- 형식 — 호출자 계약은 Javadoc 블록, 구현의 "왜"는 라인 주석(`//`)에 둔다.
- 태그 규율 — `@return`과 자명한 `@param`은 두지 않고, `@param`은 record 컴포넌트나 비자명한 제약에만 붙인다.
- 인라인 일관성 — 동종 `@Query` 정렬 근거 주석을 일관되게 둔다. 관측 발산: `OrderRepository`의 `@Query` 정렬에 `ProductRepository`·`CouponRepository`에 있는 "UUIDv7=최신순" 근거 주석 누락.

---

## 슬라이스 (1·2 독립, 3은 마지막)

### 1. `@throws` 파리티 — 도메인 예외·낙관락 충돌 문서화
- 상태: 완료
- 완료 메모: 상태전이 예외를 엔티티·위임 서비스 양쪽에 문서화하고 `Money.minus` 선행조건 IAE `@throws`를 제거했다. 독립 리뷰가 값검증 도메인 예외(`InvalidVariant`/`CartItem`/`Coupon`/`Email`)의 미문서화를 발견해, 같은 두-계층 규약(안쪽 검증점 + 위임 진입점)으로 함께 정합했다. 낙관락 충돌은 기존대로 타입 doc + 전역 핸들러가 소유(메서드별 `@throws` 없음). 최종 독립 리뷰: 누락 0·오문서화 0·동작 무변경. 3(sweep)은 `@throws` 축을 재스캔할 필요 없다.
- 목표: 정상 호출자가 마주칠 수 있는 전파 예외(도메인 예외·낙관락 충돌)를 `@throws`로 적고, 선행조건 `IllegalArgumentException` 백스톱은 적지 않는다는 규칙에 코드를 정합시킨다.
- 스캔 대상: 엔티티(`@Entity`) 상태전이 메서드·정적 팩토리, 도메인 서비스(Reader/Appender/Modifier/Processor/Validator) 진입 메서드, 값 객체 연산, 리포지토리 `@Query`·파생 메서드의 public·protected 멤버 전부.
- 완료 기준:
  - 도메인 예외(`BaseException` 하위)나 낙관락 충돌을 정상 호출자에게 전파하는 모든 public·protected 메서드가 그 예외를 `@throws`로 문서화한다(협력자를 거쳐 전파돼도 대상의 관측 계약이면 적는다). 누락 0.
  - 호출자 보장 선행조건 위반으로만 나는 `IllegalArgumentException`은 `@throws`로 적지 않는다. 기존 그런 `@throws`(예: `Money.minus`)는 제거한다. 오문서화 0.
  - 실제로 어떤 예외를 던지는지 파일에서 확인하고 사유를 적는다(추측 금지).
  - 동작·시그니처 무변경(Javadoc만). `./gradlew build` green.
  - 독립 리뷰가 스캔 대상 전반에서 누락 0·오문서화 0을 전수 확인한다(관측 예시 밖 모듈 포함).
- 범위: 중.

### 2. Swagger 단일 출처 중복 제거
- 상태: 완료
- 완료 메모: web request/response DTO·도메인 Info의 `@Schema`/`@Operation` 중복 Javadoc을 제거했다(`CheckoutRequest`·응답 DTO·`OrderInfo↔OrderResponse` 등). 애노테이션에 없는 계약(교차필드·부재필드·조건부 존재·보안 거동)만 단일 위치에 유지. 컨트롤러는 최근 커밋(`9e53c82`)이 이미 정리해 재작성하지 않았다. 세션 독립 리뷰가 구현이 놓친 cart 요청 DTO 2건(`AddCartItemRequest`·`ChangeCartItemQuantityRequest`)의 컨트롤러 `@Operation` 교차중복을 추가로 잡아 수정했다. 최종: 애노테이션-Javadoc 중복 0·동작 무변경.
- 목표: web DTO·컨트롤러 계약을 `@Schema`/`@Operation` 단일 출처로 정합하고 중복 Javadoc을 제거한다.
- 스캔 대상: `module-apps/app-api`의 web/v1 request·response record 전부, 컨트롤러 클래스·메서드 Javadoc.
- 완료 기준:
  - `@Schema`/`@Operation`과 내용이 겹치는 Javadoc을 제거한다. 애노테이션에 담지 못하는 계약(교차필드 규칙 등)만 한 곳에 남긴다(중복 금지). 관측: `CheckoutRequest` 클래스 Javadoc↔`@Schema`.
  - `@Schema` description은 API 문서 계약이라 지우지 않는다 — 중복 제거는 Javadoc 쪽에서 한다.
  - 도메인 `Info`↔web `Response` 근접 중복은 각 타입이 자기 계층 관점만 서술하게 한다(Info=경계 조회 모델 역할, Response=API 응답 형태). 복붙 동일 문장 금지.
  - 동작 무변경. `./gradlew build` green.
  - 독립 리뷰가 web DTO·컨트롤러에서 애노테이션-Javadoc 중복 0을 확인한다.
- 범위: 소.

### 3. 잔여 규칙 정합 sweep
- 상태: 대기
- 목표: 1·2 밖의 규칙 발산(자기서술 요약·형식·태그·인라인 일관성)을 정리한다.
- 스캔 대상(카테고리별 전수):
  - 자기서술 공개 메서드의 이름-되풀이 요약(제거), 계약 있는데 요약 없는 곳(추가).
  - 형식 위반(계약이 `//`로, 불필요한 "왜"가 Javadoc 블록으로 뒤바뀐 곳).
  - `@return`·자명한 `@param` 제거(app-api·web 포함 전수).
  - 동종 `@Query` 정렬 근거 `//` 주석 일관성(관측: `OrderRepository`).
- 완료 기준: 위 4개 카테고리 전수 스캔 후 발산 0. 동작 무변경. `./gradlew build` green. 독립 리뷰가 카테고리별 잔여 0을 확인한다.
- 범위: 소~중.
- 순서: 마지막(1·2가 처리한 것 외 잔여만 다룬다).

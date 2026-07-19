# TODO — 주석·Javadoc 규칙 코드 적용 (2차: 요약 의무 전환)

출처: `docs/coding-conventions.md`의 "### 코드 주석·Javadoc" 재정립(정보격차 기반 선택적 요약 → **대상별 요약 의무 + 소유자 기반 면제**, 2026-07-19). 규칙 정본은 그 문서가 소유하며, 아래 슬라이스는 규칙을 코드에 정합시키는 마이그레이션이다.

1차 마이그레이션(`@throws` 파리티·Swagger 단일 출처·잔여 sweep)은 완료됐고 그 결과는 유지한다. 이번 마이그레이션은 그 위에 **새로 생긴 의무**(타입·메서드·영속 필드·enum 상수 요약)를 채우고, **새로 생긴 금지**(타입 doc의 설계 근거, 필드 doc의 동작 효과)를 걷어낸다.

## 이번 규칙 전환의 요점

1차와 방향이 반대인 항목이 있다. 이전 규칙을 기억으로 적용하지 말고 정본 문서를 로딩한다.

| 축 | 이전 규칙 | 현재 규칙 |
|---|---|---|
| 메서드 요약 | 시그니처가 계약 전부면 생략 | **모든 메서드에 한 문장**(면제 7종 제외) |
| 영속 필드 | 규정 없음 | **전부 한 문장**. 그 값이 무엇인지만 |
| enum 상수 | 규정 없음 | **전부 한 문장**. 상태 enum은 진입 조건 포함 |
| 타입 doc | 조감도(조율·불변식·정책)를 소유 | **그 타입이 무엇인지만**. 전이 규칙·동시성·설계 근거 금지 |
| 주석 언어 | 규정 없음(관행) | 한국어 + 용어집 등재 용어만 + 타입 doc 최초 병기 |
| 라인 주석 | 비자명한 "왜"만 | 동일 + **조율 메서드 단계 번호 주석** 허용 |

## 작업 규칙

- 각 항목은 하나의 슬라이스다. AGENTS.md·docs/의 규칙을 로딩하고 준수한다. 주석 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다.
- 변경은 **동작·시그니처 무변경**이다 — Javadoc·주석만 손댄다. 로직·제어흐름·공개 API 형태·테스트 기대값을 바꾸지 않는다.
- **삭제 규약**: 타입 doc·주석에서 설계 근거·정책 서술을 잘라내기 전에 `DOMAIN_MODEL.md`·`REQUIREMENTS.md`·해당 메서드 doc 중 어디가 그 내용을 소유하는지 확인하고 근거를 보고에 적는다. 소유처가 없으면 지우지 말고 적절한 위치(메서드 doc·단계 주석)로 옮긴다.
- 완료 검증은 **독립 리뷰가 주 게이트**다. 주석 존재·내용은 빌드가 강제하지 않으므로(Error Prone Javadoc 검사는 경고, 게이트 아님 → code-quality.md), `./gradlew build` green은 회귀·포맷 확인용이지 규칙 준수 증명이 아니다.
- 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(AGENTS.md 외과적 편집). 발산한 지점만 고친다.
- 슬라이스는 서로 **독립적**이다(각자 Javadoc-only·빌드 green이라 절반 머지 위험이 없다). 모듈 경계로 갈랐으므로 순서를 바꿔도 무방하나, 위에서부터 진행한다.
- 각 항목은 `상태: 대기 | 보류(결정 대기) | 완료` 마커를 가진다. 자동 루프·새 세션은 위에서부터 첫 `대기` 항목만 수행한다.
- 세션별 착수 프롬프트는 `todo-prompt.md`가 소유한다(번호 일치). 자동 진행 규약은 `loop-prompt.md`가 소유한다.

## 참조 구현

이미 현재 규칙으로 정리된 코드다. 새 슬라이스는 이 톤에 맞춘다.

- `module-domains/domain-order` 전체 — 엔티티 필드·enum 상수·서비스·Info의 기준.
- `module-apps/app-api/.../facade/`의 `CheckoutFacade`·`OrderCancellationFacade`·`OrderRefundFacade`·`PaymentConfirmationFacade`·`PendingOrderSweepFacade`·`OrderPaymentFacade` — 조율 단계 번호 주석과 타입 doc 축약의 기준.

## 면제 대상 (스캔은 하되 요약을 달지 않는다)

`@Operation`을 가진 컨트롤러 15개와 `@Schema`를 가진 web request/response DTO 49개는 애노테이션이 계약을 소유하므로 요약 대상이 아니다. 테스트 메서드는 `@DisplayName`이 소유한다(464/465 보유). 이 면제 대상에 요약을 새로 다는 것은 규칙 위반이다.

---

## 슬라이스

### 1. app-api 완료 파사드의 잔여 주석 정리
- 상태: 완료
- 목표: 이미 타입 doc을 정리한 파사드 6개에 남은 라인 주석·private doc의 규칙 발산을 걷어낸다.
- 스캔 대상: `CheckoutFacade`·`PendingOrderSweepFacade`·`PaymentConfirmationFacade`·`OrderCancellationFacade`·`OrderRefundFacade`·`OrderPaymentFacade`.
- 관측된 발산(포함하되 여기서 멈추지 말 것):
  - `@SchedulerLock` 위 4줄 라인 주석 2건(`PendingOrderSweepFacade`·`PaymentConfirmationFacade`) — 마지막 줄이 "락의 목적·기각 대안은 REQUIREMENTS.md 제약·전제가 소유한다"고 스스로 밝히면서 3줄을 더 쓴다. 국소적으로 비자명한 것은 `lockAtMostFor` 10분 선택 근거뿐이다.
  - 미터 카운터 생성 위 주석 2건 — 변수명 3개와 메트릭 문자열 3개가 이미 같은 말을 한다(What 되풀이).
  - `CheckoutFacade.deductStockOrCompensate`의 3줄 라인 주석 — 앞 2줄은 `Order.markStockDeducted()` doc과 `DOMAIN_MODEL.md`가 소유하고, 3줄째("마커 기록 실패는 아래 catch가 동기 보상한다")는 코드가 보여주는 What이다. 국소적 "왜"는 마커를 루프 뒤에 두는 순서뿐이다.
  - `PendingOrderSweepFacade.restoreDeductedStock`의 3줄 doc — 게이트 근거가 `DOMAIN_MODEL.md`와 중복이다.
- 별건으로 남길 것: `restoreLineWithRetry`의 4줄 doc이 두 파사드에 거의 동일하게 복제돼 있다. **메서드 본문도 동일한 코드 중복**이라 주석만 줄이면 원인이 남는다. 이 슬라이스에서 고치지 말고 관측 사실만 보고한다(리팩터는 동작 변경이라 이 마이그레이션 밖).
- 완료 기준: 위 4종 발산 0. 삭제분의 소유처를 확인해 근거 보고. 동작 무변경. 독립 리뷰가 6개 파일 전수 확인.
- 범위: 소.

### 2. app-api 나머지 파사드
- 상태: 완료
- 목표: 아직 손대지 않은 파사드 7개에 요약 의무를 채우고 타입 doc을 "무엇인지"로 축약한다.
- 스캔 대상: `CartViewFacade`·`ProductRegistrationFacade`·`ProductDetailFacade`·`ProductCatalogFacade`·`CouponIssuanceFacade`·`CartCommandFacade`·`MemberWithdrawalFacade`, 그리고 같은 패키지의 View record(`ProductView`·`ProductSummaryView`·`ProductVariantView`·`CartView`·`CartLineView`).
- 완료 기준: 타입 doc이 "무엇인지"만 남음(전이 규칙·동시성·설계 근거 잔존 0). 모든 메서드에 요약(면제 제외). 조율 메서드 중 단계가 셋 이상이고 순서가 의미를 가지는 것에 번호 주석. 삭제분 소유처 근거 보고. 독립 리뷰가 파일별 확인.
- 범위: 중.

### 3. app-api 비파사드 (config·auth·exception·listener)
- 상태: 완료
- 목표: 파사드 밖 app-api 코드에 규칙을 적용한다.
- 스캔 대상: `config/` 7개, `web/auth/` 3개, `exception/` 2개, `event/listener/` 1개, `ApiApplication`, `package-info`.
- 주의: 컨트롤러 15개와 web request/response DTO 49개는 `@Operation`·`@Schema`가 계약을 소유하는 면제 대상이다. 요약을 새로 달지 말고, 애노테이션과 중복되는 Javadoc이 남아 있으면 제거만 한다.
- 완료 기준: 위와 동일. 면제 대상에 요약을 추가한 건 0.
- 범위: 소.

### 4. domain-cart
- 상태: 완료
- 목표: 엔티티 필드·enum 상수·메서드 요약 의무를 채우고 타입 doc을 축약한다.
- 스캔 대상: `module-domains/domain-cart/src/main/java` 전체(12파일, 엔티티 2·enum 1).
- 완료 기준: 공통(아래 "슬라이스 공통 완료 기준").
- 범위: 소.

### 5. domain-stock
- 상태: 완료
- 스캔 대상: `module-domains/domain-stock/src/main/java` 전체(13파일, 엔티티 1·enum 2).
- 완료 기준: 공통.
- 범위: 소.

### 6. domain-payment
- 상태: 완료
- 스캔 대상: `module-domains/domain-payment/src/main/java` 전체(17파일, 엔티티 1·enum 4). `port/` 하위 게이트웨이 인터페이스 포함.
- 완료 기준: 공통.
- 범위: 중.

### 7. domain-member
- 상태: 대기
- 스캔 대상: `module-domains/domain-member/src/main/java` 전체(22파일, 엔티티 1·enum 5). `Email` VO·`EmailConverter` 포함.
- 완료 기준: 공통.
- 범위: 중.

### 8. domain-product
- 상태: 대기
- 스캔 대상: `module-domains/domain-product/src/main/java` 전체(25파일, 엔티티 2·enum 3). `NormalizedOptions` 포함.
- 인계 사항(슬라이스 2 독립 리뷰 발견): `ProductRepository.findExposedPage`는 `@Query`를 달고도 요약이 없다(`docs/coding-conventions.md`의 "`@Query`가 붙으면 요약을 둔다" 위반, main의 기존 잔여). 이 요약이 노출 술어를 파사드 후필터가 아니라 쿼리에 두는 근거(페이지 크기·총계가 노출 집합과 일치)의 소유처가 된다 — 슬라이스 2가 `ProductCatalogFacade` 타입 doc에서 지운 유일한 무소유 내용이다.
- 완료 기준: 공통.
- 범위: 중.

### 9. domain-coupon
- 상태: 대기
- 스캔 대상: `module-domains/domain-coupon/src/main/java` 전체(27파일, 엔티티 4·enum 4). `Discount`·`ValidityPeriod` VO 포함.
- 완료 기준: 공통.
- 범위: 중.

### 10. common 모듈 + app-migration
- 상태: 대기
- 스캔 대상: `module-common/common-core`(5)·`common-jpa`(5)·`common-auth`(3)·`common-messaging`(3)·`common-web`(20), `module-apps/app-migration`(2). 총 38파일.
- 주의: `common-core`의 `Money`·`ErrorCode`, `common-jpa`의 `BaseTimeEntity`·컨버터는 전 도메인이 참조하는 재사용 타입이다. 재사용 타입 doc에는 그 타입 자신의 의미만 적고, 어느 도메인이 어떻게 쓰는지는 적지 않는다.
- 인계 사항(슬라이스 3 판정): `MigrationApplication.main`에는 요약을 달지 않는다. 슬라이스 3이 `ApiApplication.main`을 같은 판정으로 두었다 — 호출자가 JVM이고 바로 위 타입 doc이 그 앱이 무엇인지 이미 말해, 요약이 이름 되풀이가 된다. 규칙의 면제 7종에 명시되진 않은 판정이므로 두 앱을 같게 간다.
- 완료 기준: 공통.
- 범위: 중.

---

## 슬라이스 공통 완료 기준

각 슬라이스는 스캔 대상 전 파일을 전수 스캔하고 아래를 만족한다.

- 요약 의무 4종(타입·메서드·영속 필드·enum 상수) 누락 0.
- 면제 7종(오버라이드·필드 반환 접근자·생성자·파생 쿼리 메서드·테스트 메서드·설명 문자열 보유 enum·애노테이션 소유 타입/멤버)에 요약을 단 곳 0.
- 타입 doc에 상태 전이 규칙·동시성 메커니즘·설계 근거 잔존 0.
- 필드 doc에 그 값이 게이트하는 동작·전이 효과 잔존 0.
- 형식 준수: 타입·메서드 요약은 3인칭 서술형, 필드·enum 상수 요약은 명사구.
- 주석 용어가 `DOMAIN_MODEL.md` 도메인 용어집과 일치. 미등재 개념을 썼으면 용어집에 등재하는 커밋을 포함한다.
- 삭제한 설계 근거는 소유처(`DOMAIN_MODEL.md`·`REQUIREMENTS.md`·메서드 doc)를 확인하고 근거를 보고에 적는다. 소유처 확인 없이 삭제한 건 0.
- 동작·시그니처 무변경. `./gradlew build` green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 위 항목별 잔여 0을 전수 확인한다.

## Modifier의 NotFound `@throws` 판정 (슬라이스 5에서 제기)

`CartModifier`는 `CartItemNotFoundException`을 `@throws`로 적고 `OrderModifier`·`StockModifier`는 적지 않아 겉보기에 발산이다. 이는 규칙("정상 호출자가 마주칠 전파 예외만 적는다")을 호출 경로별로 적용한 결과로 보이므로, 남은 슬라이스는 아래 테스트를 적용하고 결과를 보고에 밝힌다. 일괄 정합을 위해 기존 판정을 뒤집지 않는다.

- 그 미존재가 사용자 입력으로 도달 가능하면(클라이언트가 없는 id·없는 라인을 지목할 수 있으면) 계약이므로 `@throws`를 적는다.
- 그 미존재가 선행 단계가 보장하는 내부 불변식 위반이면(예: 체크아웃이 이미 읽은 재고 행) 버그 백스톱이라 적지 않는다.

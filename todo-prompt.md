# TODO 착수 프롬프트

`todo.md`의 각 슬라이스를 새 세션에서 착수할 때 그대로 붙여넣는 프롬프트다. 번호는 `todo.md`와 일치한다. 위에서부터 진행한다.

각 프롬프트는 자립적이도록 공통 규칙·규칙 요지를 앞에 담는다(서브에이전트는 이 세션 대화를 보지 못한다). 아래 코드블록만 복사해 붙여넣으면 된다.

- 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다 — 착수 시 반드시 전문을 로딩한다. 요지는 프롬프트에 담았으나 판정이 갈리면 정본을 따른다.
- 변경은 동작·시그니처 무변경(Javadoc·주석만). 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(외과적 편집).
- 한 슬라이스가 끝나면 `todo.md`에서 해당 항목을 완료 처리하고 다음 번호로 넘어간다.

---

### 공통 규칙 블록 (아래 각 프롬프트에 이미 포함돼 있다 — 참고용)

이 마이그레이션은 1차와 방향이 반대인 항목이 있다. **이전 규칙을 기억으로 적용하지 말고 정본을 로딩한다.** 메서드 요약은 "시그니처가 말하면 생략"에서 "전부 의무(면제 7종 제외)"로 바뀌었고, 영속 필드·enum 상수 요약 의무가 신설됐으며, 타입 doc은 "조감도 소유"에서 "그 타입이 무엇인지만"으로 좁아졌다.

---

### 1. app-api 완료 파사드의 잔여 주석 정리

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 이미 타입 doc을 정리한 app-api 파사드 6개에 남은 라인 주석·private doc의 규칙 발산을 걷어낸다.
동작·시그니처는 바꾸지 않는다 — Javadoc·주석만 손댄다.

규칙 정본: docs/coding-conventions.md "### 코드 주석·Javadoc" — 반드시 전문 로딩. 요지:
- 라인 주석(//)은 비자명한 "왜"만. 코드가 말하는 What 되풀이 금지. 예외는 조율 메서드의 단계 표시
  (// 1. 입력 검증)로, 단계가 셋 이상이고 순서 자체가 정보일 때만 단다.
- 타입 doc은 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
- 금지: 결정 내러티브(무엇을 왜 채택/폐기했는지는 문서에 적는다), 검사가 지키는 것 되풀이.
- 같은 사실을 두 곳에 두지 않는다 — 다른 문서·메서드가 소유하면 거기가 단일 출처다.

스캔 대상(6파일 전수):
module-apps/app-api/src/main/java/com/commerce/api/facade/ 의
CheckoutFacade, PendingOrderSweepFacade, PaymentConfirmationFacade,
OrderCancellationFacade, OrderRefundFacade, OrderPaymentFacade

관측된 발산(반드시 포함하되 여기서 멈추지 말 것):
1) @SchedulerLock 위 4줄 라인 주석 2건(PendingOrderSweepFacade, PaymentConfirmationFacade).
   마지막 줄이 "락의 목적·기각 대안은 REQUIREMENTS.md 제약·전제가 소유한다"고 스스로 밝히면서
   3줄을 더 쓴다. 크래시 회수·겹침 무해·획득 실패 재시도는 ShedLock 일반 동작이다. 국소적으로
   비자명한 것은 lockAtMostFor를 왜 10분으로 두는가뿐 — 그 한 줄만 남긴다.
2) 미터 카운터 생성 위 주석 2건("조용한 잔존·보상 실패를 수치로 관측한다 — ...").
   변수명(processedOrders/failedOrders/skippedStockRestores)과 메트릭 문자열이 이미 같은 말을 한다.
3) CheckoutFacade.deductStockOrCompensate의 3줄 라인 주석. 앞 2줄은 Order.markStockDeducted() doc과
   DOMAIN_MODEL.md가 소유하고, 3줄째("마커 기록 실패는 아래 catch가 동기 보상한다")는 바로 아래
   catch가 보여주는 What이다. 국소적 "왜"는 마커를 루프 뒤에 두는 순서뿐이다.
4) PendingOrderSweepFacade.restoreDeductedStock의 3줄 doc — 게이트 근거가 DOMAIN_MODEL.md와 중복.
   자기 계약(증거 있는 주문만 복원)만 한 줄로 남긴다.

이 슬라이스에서 고치지 말 것:
- restoreLineWithRetry의 4줄 doc이 PendingOrderSweepFacade와 PaymentConfirmationFacade에 거의
  동일하게 복제돼 있다. 메서드 본문도 동일한 코드 중복이라 주석만 줄이면 원인이 남는다. 리팩터는
  동작 변경이라 이 마이그레이션 밖이다 — 관측 사실만 보고하고 코드는 건드리지 않는다.

삭제 규약(중요): 주석을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·REQUIREMENTS.md·해당 메서드 doc 중
어디가 소유하는지 grep으로 확인하고 근거를 보고에 적는다. 소유처가 없으면 지우지 말고 적절한
위치(메서드 doc·단계 주석)로 옮긴다.

완료 기준:
- 위 4종 발산 0. 각 삭제분의 소유처를 확인한 근거를 보고에 포함.
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 6개 파일을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + docs/architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 2. app-api 나머지 파사드

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"을 아직 손대지 않은 app-api 파사드에 적용한다.
동작·시그니처는 바꾸지 않는다 — Javadoc·주석만 손댄다.

규칙 정본은 위 문서다. 반드시 전문을 로딩한다. 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이
있으니 기억으로 적용하지 말 것. 요지:
- 주석은 한국어. 용어는 DOMAIN_MODEL.md 도메인 용어집에 등재된 것만 쓰고, 미등재 개념은 용어집에
  먼저 올린다. 도메인 개념이 타입 doc에 처음 나오면 `한국어(영문식별자)`로 병기한다.
- 요약은 타입·메서드·영속 필드·enum 상수에 둔다(각 한 문장).
  · 타입 = 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
  · 메서드 = 공개면 호출자 계약(무엇을 보장하나), 내부면 그 메서드가 맡은 일.
  · 필드 = 그 값이 무엇인지만. 그 값이 게이트하는 동작·전이 효과는 적지 않는다.
  · enum 상수 = 의미. 상태 enum이면 진입 조건을 더하고 상수당 한 줄을 넘기지 않는다.
- 요약을 두지 않는 자리(다른 데가 의미를 이미 소유): 상위 계약을 그대로 따르는 오버라이드 / 필드
  반환 한 줄 접근자(get*·is*) / 생성자 / 이름이 곧 쿼리 명세인 파생 쿼리 메서드(@Query가 붙으면
  요약을 둔다) / 테스트 메서드(@DisplayName) / 상수가 설명 문자열을 직접 든 enum / API 계약을
  @Operation·@Schema가 소유하는 타입·멤버.
- 형식: 타입·메서드 요약은 3인칭 서술형, 필드·enum 상수 요약은 명사구. 계약은 /** */, 구현의
  "왜"는 //. 라인 주석은 비자명한 "왜"만 — 예외는 조율 메서드의 단계 표시(// 1. 입력 검증)로,
  단계가 셋 이상이고 순서 자체가 정보일 때만 단다.
- 금지: 결정 내러티브, 검사가 지키는 것 되풀이, 주석 처리된 코드, 작성자·날짜.

참조 구현(이미 이 규칙으로 정리됨 — 톤을 맞춘다):
- module-domains/domain-order 전체
- 같은 facade 패키지의 CheckoutFacade, OrderCancellationFacade, OrderRefundFacade,
  PaymentConfirmationFacade, PendingOrderSweepFacade, OrderPaymentFacade
  특히 CheckoutFacade.checkout()의 8단계 번호 주석이 조율 메서드의 기준이다.

스캔 대상(전수):
module-apps/app-api/src/main/java/com/commerce/api/facade/ 의
CartViewFacade, ProductRegistrationFacade, ProductDetailFacade, ProductCatalogFacade,
CouponIssuanceFacade, CartCommandFacade, MemberWithdrawalFacade
및 같은 패키지의 View record: ProductView, ProductSummaryView, ProductVariantView,
CartView, CartLineView

삭제 규약(중요): 타입 doc에서 설계 근거·정책 서술을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·
REQUIREMENTS.md·해당 메서드 doc 중 어디가 소유하는지 grep으로 확인하고 근거를 보고에 적는다.
소유처가 없으면 지우지 말고 적절한 위치(메서드 doc·단계 주석)로 옮긴다.

완료 기준:
- 대상 전 파일 전수 스캔. 요약 의무 누락 0, 면제 대상에 요약을 단 곳 0.
- 타입 doc에 전이 규칙·동시성·설계 근거 잔존 0.
- 조율 메서드 중 단계가 셋 이상이고 순서가 의미를 가지는 것에 번호 주석. 둘 이하에는 달지 않는다.
- 삭제한 설계 근거의 소유처 확인 근거를 보고에 포함. 소유처 확인 없이 삭제한 건 0.
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 파일별 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + docs/architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 3. app-api 비파사드 (config·auth·exception·listener)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"을 app-api의 파사드 밖 코드에 적용한다.
동작·시그니처는 바꾸지 않는다 — Javadoc·주석만 손댄다.

규칙 정본은 위 문서다. 반드시 전문을 로딩한다. 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이
있으니 기억으로 적용하지 말 것. 요지:
- 주석은 한국어. 용어는 DOMAIN_MODEL.md 도메인 용어집에 등재된 것만 쓰고, 미등재 개념은 용어집에
  먼저 올린다. 도메인 개념이 타입 doc에 처음 나오면 `한국어(영문식별자)`로 병기한다.
- 요약은 타입·메서드·영속 필드·enum 상수에 둔다(각 한 문장).
  · 타입 = 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
  · 메서드 = 공개면 호출자 계약, 내부면 그 메서드가 맡은 일.
  · 필드 = 그 값이 무엇인지만. enum 상수 = 의미(상태면 진입 조건).
- 요약을 두지 않는 자리: 오버라이드 / 필드 반환 한 줄 접근자 / 생성자 / 파생 쿼리 메서드 /
  테스트 메서드(@DisplayName) / 상수가 설명 문자열을 직접 든 enum / API 계약을 @Operation·@Schema가
  소유하는 타입·멤버.
- 형식: 타입·메서드 요약은 3인칭 서술형, 필드·enum 상수 요약은 명사구. 계약은 /** */, "왜"는 //.
- 금지: 결정 내러티브, 검사가 지키는 것 되풀이, 주석 처리된 코드, 작성자·날짜.

스캔 대상(전수):
module-apps/app-api/src/main/java/com/commerce/api/ 의
- config/ 7파일
- web/auth/ 3파일
- exception/ 2파일
- event/listener/ 1파일
- ApiApplication.java, package-info.java

면제 대상(스캔은 하되 요약을 새로 달지 말 것):
- 컨트롤러 15개 — 전부 @Operation을 가진다. 계약은 애노테이션이 소유한다.
- web/v1 하위 request/response record 49개 — 전부 @Schema를 가진다. 계약은 애노테이션이 소유한다.
- 이 면제 대상에 애노테이션과 중복되는 Javadoc이 남아 있으면 제거만 한다. 요약 추가는 규칙 위반이다.
  (@Schema description은 API 문서 계약이라 지우지 않는다 — 중복 제거는 Javadoc 쪽에서 한다.)

삭제 규약(중요): 주석을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·REQUIREMENTS.md·해당 메서드 doc 중
어디가 소유하는지 확인하고 근거를 보고에 적는다. 소유처가 없으면 옮긴다.

완료 기준:
- 대상 전 파일 전수 스캔. 요약 의무 누락 0, 면제 대상에 요약을 단 곳 0.
- 애노테이션-Javadoc 중복 0.
- 동작·시그니처 무변경. ./gradlew build green.
- 독립 리뷰(구현과 분리된 서브에이전트)가 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 4. domain-cart

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"을 module-domains/domain-cart에 적용한다.
동작·시그니처는 바꾸지 않는다 — Javadoc·주석만 손댄다.

규칙 정본은 위 문서다. 반드시 전문을 로딩한다. 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이
있으니 기억으로 적용하지 말 것(메서드 요약은 "생략 가능"에서 "전부 의무"로, 영속 필드·enum 상수
요약 의무는 신설, 타입 doc은 "조감도 소유"에서 "무엇인지만"으로 바뀌었다). 요지:
- 주석은 한국어. 용어는 DOMAIN_MODEL.md 도메인 용어집에 등재된 것만 쓰고, 미등재 개념은 용어집에
  먼저 올린다. 도메인 개념이 타입 doc에 처음 나오면 `한국어(영문식별자)`로 병기한다.
- 요약은 타입·메서드·영속 필드·enum 상수에 둔다(각 한 문장).
  · 타입 = 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
  · 메서드 = 공개면 호출자 계약(무엇을 보장하나), 내부면 그 메서드가 맡은 일.
  · 필드 = 그 값이 무엇인지만. 형제 필드와의 구분, 단위·스케일·시간대, 값 범위·존재 조건, 다른
    필드와의 산식이 대상이다. 그 값이 게이트하는 동작·전이 효과는 적지 않는다(그 동작을 하는
    메서드가 소유한다). 대상은 @Entity·@Embeddable의 영속 필드이며, record면 컴포넌트마다 @param.
  · enum 상수 = 의미. 상태 enum이면 진입 조건을 더하고 상수당 한 줄을 넘기지 않는다(전이 전체는
    DOMAIN_MODEL.md 상태 전이 요약이 소유한다).
- 요약을 두지 않는 자리(다른 데가 의미를 이미 소유): 상위 계약을 그대로 따르는 오버라이드 / 필드
  반환 한 줄 접근자(get*·is*, 단 has*·can*는 항상 요약을 둔다) / 생성자 / 이름이 곧 쿼리 명세인
  파생 쿼리 메서드(@Query가 붙으면 요약을 둔다) / 테스트 메서드(@DisplayName) / 상수가 설명 문자열을
  직접 든 enum(ErrorCode 계열).
- 정상 호출자가 마주칠 전파 예외는 @throws로 적는다. 선행조건 위반으로만 나는 IllegalArgumentException
  (버그 백스톱)은 계약이 아니라 적지 않는다. @return과 자명한 @param은 두지 않는다.
- 형식: 타입·메서드 요약은 3인칭 서술형(`사용자를 등록한다`), 필드·enum 상수 요약은 명사구
  (`최초 등록 시각`). 마침표로 끝낸다. 계약은 /** */, 구현의 "왜"는 //.
- 라인 주석은 비자명한 "왜"만. 예외는 조율 메서드의 단계 표시(// 1. 입력 검증)로 단계가 셋 이상이고
  순서 자체가 정보일 때만.
- 금지: 결정 내러티브, 검사가 지키는 것 되풀이, 주석 처리된 코드, 작성자·날짜.

참조 구현(이미 이 규칙으로 정리됨 — 톤을 맞춘다): module-domains/domain-order 전체.
특히 Order.java(필드 25개), OrderStatus/FulfillmentStatus(상태 enum 진입 조건),
CancellationReason(사유 enum), OrderReader/OrderModifier(서비스 doc)를 기준으로 삼는다.

스캔 대상: module-domains/domain-cart/src/main/java 전체(12파일, 엔티티 2·enum 1).
entity·info·service·repository·exception·event 전 패키지.

삭제 규약(중요): 타입 doc에서 설계 근거·정책 서술을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·
REQUIREMENTS.md·해당 메서드 doc 중 어디가 소유하는지 grep으로 확인하고 근거를 보고에 적는다.
소유처가 없으면 지우지 말고 적절한 위치로 옮긴다.

완료 기준:
- 대상 전 파일 전수 스캔. 요약 의무 4종 누락 0, 면제 대상에 요약을 단 곳 0.
- 타입 doc에 전이 규칙·동시성·설계 근거 잔존 0. 필드 doc에 동작·전이 효과 잔존 0.
- 형식 준수(타입·메서드=서술형, 필드·enum 상수=명사구).
- 주석 용어가 DOMAIN_MODEL.md 용어집과 일치. 미등재 개념을 썼으면 용어집 등재를 커밋에 포함한다.
- 삭제한 설계 근거의 소유처 확인 근거를 보고에 포함. 소유처 확인 없이 삭제한 건 0.
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 항목별 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + docs/architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 5. domain-stock

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-domains/domain-stock
스캔 대상: module-domains/domain-stock/src/main/java 전체(13파일, 엔티티 1·enum 2).
추가 주의: StockStatus는 수동 품절·단종을 quantity=0과 분리하는 상태 enum이다. 각 상수의 의미와
진입 조건을 적되, 차감·복원 정책(낙관락 가드·복원 게이트)은 DOMAIN_MODEL.md와 메서드 doc이
소유하므로 타입 doc에 옮겨 적지 않는다.
```

### 6. domain-payment

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-domains/domain-payment
스캔 대상: module-domains/domain-payment/src/main/java 전체(17파일, 엔티티 1·enum 4).
port/ 하위 게이트웨이 인터페이스(GatewayTransactionStatus 등) 포함.
추가 주의:
- PaymentErrorCode는 상수가 설명 문자열을 직접 들고 있는 enum이라 요약 면제 대상이다.
- 리컨실·웹훅 확정 경로의 설계 근거는 DOMAIN_MODEL.md "미확정 결제 리컨실" 절과 REQUIREMENTS.md가
  소유한다. 타입 doc에 옮겨 적지 않는다.
- 참조 구현으로 app-api의 PaymentConfirmationFacade(이미 정리됨)를 함께 본다.
```

### 7. domain-member

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-domains/domain-member
스캔 대상: module-domains/domain-member/src/main/java 전체(22파일, 엔티티 1·enum 5).
Email 값 객체와 EmailConverter(JPA AttributeConverter) 포함.
추가 주의:
- 재사용 타입(Email VO, MemberStatus·SuspensionReason·WithdrawalReason enum)의 doc에는 그 타입
  자신의 의미만 적는다. 그 값이 어느 필드에서 언제 존재하는지(예: "SUSPENDED에서만 존재")나 인접
  필드가 무엇을 담는지(예: "탈퇴는 deletedAt으로 표현")는 그 필드·엔티티의 계약이므로 적지 않는다.
- 탈퇴는 상태가 아니라 deletedAt으로 표현한다 — 이 사실은 Member의 해당 필드 doc이 소유한다.
```

### 8. domain-product

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-domains/domain-product
스캔 대상: module-domains/domain-product/src/main/java 전체(25파일, 엔티티 2·enum 3).
NormalizedOptions 포함.
추가 주의:
- ProductVariant의 optionSignature와 optionLabel은 이름만으로 구분되지 않는 형제 필드다. 필드 doc이
  둘의 차이를 명확히 갈라야 한다(규칙의 "형제 필드와의 구분" 항목).
- 부분 유니크 인덱스 술어가 enum 문자열 'RETIRED'를 참조한다는 사실은 DOMAIN_MODEL.md
  "영속·마이그레이션 유의"가 소유한다 — 타입 doc에 옮겨 적지 않는다.
- 참조 구현으로 app-api의 ProductDetailFacade(슬라이스 2에서 정리됨)를 함께 본다.
```

### 9. domain-coupon

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-domains/domain-coupon
스캔 대상: module-domains/domain-coupon/src/main/java 전체(27파일, 엔티티 4·enum 4).
Discount(판별형 값 객체)·ValidityPeriod 포함.
추가 주의:
- Discount는 Fixed/Rate를 한 VO로 판별하는 타입이다. 타입 doc은 그것이 무엇인지까지만 적고,
  판별형을 택한 근거(sealed 하위 타입 + AttributeConverter 기각 등)는 docs/entity-persistence.md와
  DOMAIN_MODEL.md가 소유한다.
- ValidityPeriod는 발급 가능 기간이고 발급분 사용 기한은 IssuedCoupon.expiresAt이다. 두 개념이
  섞이지 않게 각 타입·필드 doc이 자기 것만 적는다.
- CouponErrorCode는 요약 면제 대상(상수가 설명 문자열 보유).
```

### 10. common 모듈 + app-migration

```
[4번 프롬프트와 동일하되 아래만 교체]

작업 대상: module-common 전체 + module-apps/app-migration
스캔 대상(총 38파일):
- module-common/common-core (5) — Money, ErrorCode, BaseException, UuidV7Generator 등
- module-common/common-jpa (5) — BaseTimeEntity, MoneyConverter, SchemaFlywayFactory 등
- module-common/common-auth (3)
- module-common/common-messaging (3)
- module-common/common-web (20)
- module-apps/app-migration (2)

추가 주의:
- 여기 타입들은 전 도메인이 참조하는 재사용 타입이다. 재사용 타입 doc에는 그 타입 자신의 의미만
  적고, 어느 도메인이 어떻게 쓰는지는 적지 않는다(규칙 "문서화 대상 자신의 계약만 적는다").
- ErrorCode 인터페이스와 그 구현 enum은 다르다. 인터페이스는 계약을 적고, 상수가 설명 문자열을 직접
  든 구현 enum(WebErrorCode 등)은 상수 요약 면제 대상이다.
- Money 같은 값 객체의 연산 메서드는 선행조건 위반 IllegalArgumentException을 @throws로 적지 않는다
  (버그 백스톱). 이는 1차 마이그레이션에서 이미 정리됐으니 되돌리지 않는다.
- common-jpa의 BaseTimeEntity는 전 엔티티의 상위 타입이다. 상위 계약을 그대로 따르는 하위
  오버라이드는 요약 면제 대상이므로, 상위 doc이 계약을 제대로 담고 있는지 확인한다.
```

# TODO 착수 프롬프트

`todo.md`의 각 슬라이스를 새 세션에서 착수할 때 그대로 붙여넣는 프롬프트다. 번호는 `todo.md`와 일치한다. 위에서부터 순서대로 진행한다.

각 프롬프트는 자립적이도록 공통 규칙을 앞에 담는다. 아래 코드블록만 복사해 붙여넣으면 된다.

- 선행 결정은 확정돼 각 프롬프트에 `결정` 라인으로 반영돼 있다 — 되묻지 않고 그대로 진행한다.
- 한 슬라이스가 끝나면 `todo.md`에서 해당 항목을 완료 처리하고 다음 번호로 넘어간다.

---

## 반드시 (출시 차단)

### 1. 취소·환불 복원 exactly-once 게이트

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 가정·해석을 밝히고 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 취소·환불·스윕·리컨실의 재고·쿠폰 복원을 정확히 한 번으로 게이트한다.
- 문제(Critical): 완결된 취소·환불을 Idempotency-Key 헤더 없이 재호출하면 복원이 무게이트로
  재실행된다. OrderCancellationFacade.java:80-83이 이미 CANCELLED인 주문을 관용 통과시킨 뒤 :61-77이
  전이 발생 여부와 무관하게 전 라인 재고 restore(가산, Stock.java:74-77) + 쿠폰 restoreUse를 재실행해
  재고가 호출 횟수만큼 증식한다. 더 심각하게 IssuedCoupon.restoreUse()(IssuedCoupon.java:114)가
  orderId를 검증하지 않아, 주문 A 취소 완결 후 그 쿠폰을 주문 B에 재사용(USED)한 상태에서 주문 A
  취소를 재호출하면 B가 쓰는 쿠폰이 ISSUED로 풀린다(쿠폰 재장전, 할인 반복 수취).
  OrderRefundFacade.java:76-84도 동일 패턴이다. REQUIREMENTS.md:145 "정확히 한 번만 복원" 위반이며
  DOMAIN_MODEL.md:517("전이가 실제 일어난 호출에서만 보상")과 :628이 문서 내부에서 상충한다.
- 결정(2026-07-16 확정): DOMAIN_MODEL.md:689 "복원은 1회성 전이가 게이트한다"를 코드로 관철한다.
  (a) restoreUse에 orderId를 넘겨 사용-주문 일치일 때만 복원, (b) 이미 CANCELLED/REFUNDED인 관용 통과
  경로는 복원을 재실행하지 않고 취소 전이가 이 호출에서 실제 일어났을 때만 복원을 태운다,
  (c) 체크아웃 동기 보상도 스윕·리컨실과 같은 취소-선행 → 복원-후행 순서로 통일한다.
- 완료 기준: restoreUse(issuedCouponId, orderId)로 시그니처를 바꿔 status==USED && orderId 일치일 때만
  복원(전 호출부 5곳 CheckoutFacade·OrderCancellationFacade·OrderRefundFacade·PendingOrderSweepFacade·
  PaymentConfirmationFacade 정합). 관용 통과 경로는 복원을 재실행하지 않도록 취소 전이가 이 호출에서
  실제 일어났는지로 게이트. 체크아웃 보상을 취소-선행 순서로 통일. IT: 취소 완결 후 재호출이 재고·쿠폰을
  추가로 건드리지 않음 + 크로스-주문 쿠폰 재장전 차단.
- 완료 후 ./gradlew build 통과 + architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 2. APPROVED 결제 리컨실 사각지대 봉합

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: APPROVED 결제가 리컨실 관할 밖으로 새는 영구 사각지대를 봉합한다.
- 문제(High): PaymentProcessor.approve가 APPROVED를 자기 트랜잭션으로 먼저 커밋하고
  (PaymentProcessor.java:62-63) markPaid는 별도 트랜잭션·try 밖이다(CheckoutFacade.java:119-120). 둘
  사이 크래시나 markPaid 예외 시 payment=APPROVED, order=PENDING이 남는데, 결제 리컨실은 REQUESTED만
  스윕하고 PENDING 스윕은 payment 행이 있으면 위임한다(PendingOrderSweepFacade.java:91-94) — 위임받을
  쪽이 이 결제를 영영 안 본다(돈은 빠졌는데 주문 영구 PENDING, 로그·알람 없음). 리컨실 고아 환불 분기도
  승인 기록 커밋 후 환불을 호출해(PaymentConfirmationFacade.java:135-138) 환불 실패 시 APPROVED로 굳어
  스윕 대상에서 이탈한다(자기복구 상실). REQUIREMENTS.md:149·DOMAIN_MODEL.md:687 위반.
- 결정(2026-07-16 확정): 결제 리컨실 대상에 "비REQUESTED(APPROVED) 결제 × 주문 미종결/종결"을 포함한다 —
  APPROVED × PENDING은 markPaid 완결, APPROVED × 취소·환불은 고아 청구 환불.
- 완료 기준: 결제 리컨실이 "APPROVED × 주문 PENDING → markPaid 완결", "APPROVED × 취소·환불 주문 → 고아
  청구 환불"을 처리한다. 고아 환불은 승인 기록과 환불을 자기복구 가능한 순서(환불 먼저 또는 한 커밋)로
  재배치한다. 크래시 주입 IT로 두 사각지대가 다음 스윕에 수렴함을 검증.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 3. 주문·결제 생명주기 낙관락

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 주문·결제 취소 상태 전이를 낙관락으로 직렬화한다.
- 문제(High): Order·Payment 어디에도 @Version이 없다. 취소 전이 가드는 load-check-write라, 동시 취소
  2건이 겹치면 둘 다 PAID/APPROVED를 읽고 가드를 통과해 last-write-wins로 둘 다 커밋 → 두 스레드 모두
  재고·쿠폰 복원 루프를 실행한다(이중 가산). PG 실환불만 결정론적 멱등 키가 방어한다.
  DOMAIN_MODEL.md:563("결제는 동시 경합이 없다")·:689("전이 가드가 정확히-1회 복원 보장")·
  REQUIREMENTS.md:145("1회성 전이가 구조적으로 거부")가 거짓 전제가 된다.
- 결정(2026-07-16 확정): docs/entity-persistence.md:105 "경합 민감 상태전이만 @Version 승격"에 따라
  주문의 취소·환불 전이(및 결제 취소)를 경합 민감으로 인정하고 @Version + 409 매핑으로 승격한다.
  상태 조건부 UPDATE(where status='PAID', 영향 행 0이면 복원 스킵) 대안도 허용한다.
- 완료 기준: 주문 상태 전이(및 결제 취소)를 낙관락으로 직렬화(@Version + Flyway version 컬럼 +
  ddl-auto=validate 정합, 또는 상태 조건부 UPDATE). ObjectOptimisticLockingFailureException → 409 매핑
  (docs/entity-persistence.md:107). 동시 취소 2건 IT로 복원이 정확히 한 번임을 검증.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 4. PENDING 스윕 재고 복원 안전화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: PENDING 스윕이 차감되지 않은 재고까지 복원하는 재고 증식을 막는다.
- 문제(High): PendingOrderSweepFacade.java:99-106이 차감 진행도를 모른 채 전 라인을 무조건 restore한다.
  체크아웃이 주문 PENDING 커밋 직후(차감 전) 또는 라인 j/n 차감 중 크래시하면 차감된 적 없는 라인까지
  +qty → 실물보다 부푼 재고 → 오버셀. 동기 보상은 deducted 리스트로 실제 차감분만 복원하는데
  (CheckoutFacade.java:220-231) 스윕만 이 정밀도가 없다. DOMAIN_MODEL.md:658은 잔존을 "차감 재고가
  남은" 상태로만 가정해 place 커밋~차감 완료 사이 크래시를 가정 밖에 둔다.
- 결정(2026-07-16 확정): 과복원(재고 증식→오버셀)보다 과소복원(팬텀 품절)이 안전하다. 차감 완료 증거
  (주문 단위 차감 마커) 없이 재고를 복원하지 않고, 증거가 있는 라인만 복원한다.
- 완료 기준: 주문 단위 차감 완료 증거(마커)를 남기고, 스윕은 증거 있는 라인만 복원한다(증거 없으면 복원
  생략 → 팬텀 품절, 운영 대사로 강등). 크래시 타이밍별 IT로 재고 증식이 없음을 검증. 채택한 잔여 방향을
  문서에 명시.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 5. 장바구니 쓰기 경로 동시성 견고화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 장바구니 쓰기 경로의 동시성 무방비를 product/stock과 같은 이중화 패턴으로 닫는다.
- 문제(High): CartAppender.java:22-24의 get-or-create가 무방비다. 동시 첫 담기 2건 → 둘 다
  findByMemberId 빈 결과 → 둘 다 save → cart.member_id 유니크 위반 → 어떤 핸들러도 안 잡아 500.
  동일 변형 동시 담기 → cart_item(cart_id,variant_id) 유니크 위반 → 500. 라인 있는 동시 합산은
  CartItem.java:57-60의 메모리 read-modify-write + @Version 부재라 합산이 소실된다. product·stock
  Appender는 동일 경합을 saveAndFlush+catch로 방어하는데 cart만 이탈한다.
- 완료 기준: 유니크 위반 catch 후 재조회-재시도(get-or-create), 수량 합산을 원자적(@Version 또는 조건부
  UPDATE)으로. 동시 담기 IT로 500 부재 + 합산 정확성 검증. product/stock의 기존 이중화 패턴과 정합.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 6. 상품 등록 중단-재개 로직

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 상품 등록 중단 시 남은 단계를 재개하도록 파사드를 고친다.
- 문제(High): ProductRegistrationFacade.addVariant가 무조건 변형 create부터 시작한다
  (ProductRegistrationFacade.java:55-60). 변형 create 성공 후 재고 create 전에 중단되면, 재시도가 비-
  RETIRED 중복 검사(ProductVariantAppender.java:50-53)에 걸려 409로 영구 차단되고, 재고 시딩용 관리자
  API도 없어(StockController에 create 엔드포인트 부재) retire 후 재등록 외엔 복구 불가하다.
  DOMAIN_MODEL.md:673 "중단 복구는 남은 단계를 재개한다(재생성이 아니라 재개)"를 위반한다.
- 완료 기준: addVariant가 동일 시그니처의 비-RETIRED 변형이 이미 있으면 그 변형으로 남은 단계(재고 존재
  확인 → create → enable)를 이어가는 재개 분기를 갖는다. 중단 지점별(변형만/재고 전/미활성) IT로 재개가
  성공함을 검증.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

---

## 권장

### 7. 보상 복원 유실 트리거 축소

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 보상 복원 루프가 일시 예외 하나로 남은 라인을 영구 유실하지 않게 한다.
- 문제(Medium): 리컨실 거절 보상(PaymentConfirmationFacade.java:148-157)과 PENDING 스윕
  (PendingOrderSweepFacade.java:99-106)의 복원 루프가, 취소 전이 커밋 후 라인 복원 중 일시 예외(동시
  체크아웃과의 재고 낙관락 충돌 등) 하나면 끊기고 다음 스윕은 주문이 이미 CANCELLED라 복원 분기를
  건너뛴다 → 남은 라인 복원이 영구 유실. DOMAIN_MODEL.md:653은 이를 "중단(크래시)"으로만 서술해 발생
  빈도를 과소 표현한다.
- 완료 기준: 복원 루프에 라인 단위 재시도를 넣는다(restore는 가산·교환법칙이라 재시도 안전 —
  DOMAIN_MODEL.md:689가 근거 제공). 일시 충돌 주입 IT로 남은 라인이 결국 복원됨을 검증. 문서의 잔여
  서술을 실제 트리거 범위로 정정. (#1·#3의 복원 게이트·낙관락과 정합.)
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 8. 쿠폰 use 도메인 계약 소유 검증

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 쿠폰 use가 소유(memberId)를 도메인에서 강제하게 한다.
- 문제(Medium): IssuedCouponModifier.use가 findById로 로드해 소유(memberId)·산출할인>0을 검증하지
  않는다(IssuedCouponModifier.java:33-36, 엔티티는 상태·기한만 가드 IssuedCoupon.java:87-97).
  DOMAIN_MODEL.md:415는 use 거부에 "미존재(미소유 포함)"를 명시한다. 현 유일 호출 경로는 파사드 preview가
  findByIdAndMemberId로 선방어하나, 미래 호출자(신규 파사드·배치)가 타인 쿠폰을 사용 처리할 수 있다.
- 완료 기준: use(issuedCouponId, memberId, orderId)로 소유를 도메인에서 강제(findByIdAndMemberId).
  타인 쿠폰 use가 미존재로 거부됨을 단위/IT로 검증. (#1의 restoreUse orderId 강제와 시그니처 방향 정합.)
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 9. 장바구니 뷰 판매성 파생 반영

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 장바구니 뷰가 라인별 판매성 파생을 카탈로그와 같은 합성으로 반영하게 한다.
- 문제(Medium): CartViewFacade.java:37-56이 변형 상태·상품 상태·재고를 전혀 읽지 않아, DISABLED/RETIRED
  변형·HIDDEN/삭제 상품 라인이 정상가·소계로 표시되고 총액에도 합산된다(체크아웃에서야 거부).
  DOMAIN_MODEL.md:135 "장바구니 표시는 카탈로그와 같은 파생을 읽기로 반영한다"를 위반한다.
- 완료 기준: 라인별 orderable/unavailable 파생 플래그를 카탈로그와 같은 합성(IN 배치 조회)으로 추가.
  뷰 IT로 비활성·삭제 라인이 unavailable로 표시됨을 검증. N+1 없이.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 10. 공개 상품 상세 HIDDEN 가시성

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 공개 상품 상세가 HIDDEN·삭제 상품을 노출하지 않게 한다.
- 문제(Medium): 공개 상세(ProductController.java:112-115, 게이트 없음)가 deletedAt만 필터하고
  (ProductDetailFacade.java:46-47) HIDDEN 상품을 노출한다. 비로그인 사용자가 ID만 알면 관리자가 일시
  중지한 상품의 이름·설명·가격을 조회할 수 있다. 기존 테스트가 이 노출을 의도로 고정해 스펙-구현 충돌
  상태다.
- 결정(2026-07-16 확정): DOMAIN_MODEL.md:125 "HIDDEN = 그룹 전체 노출·주문 불가"를 정본으로 본다.
  공개 상세도 HIDDEN·삭제 상품을 404로 은닉하고, 노출을 고정한 기존 테스트를 정합한다.
- 완료 기준: 공개 상세에서 HIDDEN·삭제 상품을 404로 은닉(카탈로그 목록과 동일 파생). 노출을 고정한 기존
  테스트를 404 기대로 정합. 관리자 표면의 숨김 포함 조회는 유지.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 11. 핵심 보상·경합 테스트 공백 보강

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 최악 시나리오를 특성화하는 테스트 공백을 메운다.
- 문제(Medium): 실 인프라 위 466개 테스트에도 다음이 코드 배치로만 존재한다 — (a) 쿠폰 이중 사용 실경합
  (같은 쿠폰 × 동시 두 체크아웃 → 한쪽만): IssuedCoupon.@Version이 유일 방어인데 실경합 미검증.
  (b) PG 환불 실패 시 주문 PAID 유지·복원 없음: paymentGateway.cancel 예외 주입 테스트 부재.
  (c) 완전 성공 후 중복 환불의 재고 축: OrderRefundFacadeTest가 두 번째 refund를 실행하고도 재고를 안
  봐(실제 +1 재가산) 경계를 특성화하지 않음. (d) 멱등 필터 Redis 장애 거동 미검증. 부수로 다중 라인 주문
  보상 시나리오 전무(전부 단일 라인).
- 완료 기준: (a) CheckoutConcurrencyTest 패턴을 재사용한 쿠폰 이중 사용 경합 테스트, (b) PG 환불 실패
  주입으로 PAID 유지·복원 없음 검증, (c) 중복 취소·환불 후 재고 상태 특성화(#1 수정 후 "추가 복원 없음"
  으로), (d) 멱등 필터 Redis 장애 fail-closed 테스트, (e) 다중 라인 보상 IT 최소 1건. (#1·#3 구현 후
  회귀 고정 역할이라 그 항목 완료 이후 착수를 권장.)
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 12. 관측성 최소셋 (메트릭 + 요청 상관관계 ID)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 정합 결함의 조용한 잔존을 감지할 관측 최소셋을 배선한다.
- 문제(Medium): actuator는 health만 노출하고 Micrometer 레지스트리·traceId/X-Request-Id/MDC·요청 로깅·
  에러 추적이 전무하다(grep 0건). #2·#4·#7 같은 "조용한 잔존·보상 실패"를 감지할 수단이 없다.
  OrderPaidListener처럼 삼킨 예외도 log.warn뿐이다.
- 완료 기준: Micrometer 메트릭 노출(커넥션 풀·스윕 처리 건수·409 비율 등 최소셋) + 요청 상관관계
  ID(MDC) 배선 + 보상/스윕 실패 경고 로그. app-migration 포함 구조화 로깅 정합(#23과 조율). PII·시크릿
  미노출 유지.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 13. 문서-코드 정합 (아웃박스 과잉 주장·품질 게이트 서술)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 문서가 실제보다 과하게 주장하는 지점을 코드 실배선에 맞춘다.
- 문제(Medium): docs/architecture.md:99가 common-messaging을 "발행 포트·아웃박스·멱등 소비 지원"이라
  적지만 실제는 마커 인터페이스+포트 1개다(아웃박스는 DOMAIN_MODEL.md:767이 범위 밖으로 선언).
  docs/code-quality.md:25-26의 NullAway "@NullMarked 범위에서만 검사" 서술이 실배선
  (AnnotatedPackages=com.commerce 전역 검사)과 다르고, :38의 "Error Prone이 Javadoc 구조를 컴파일
  시점에 강제"는 게이트가 아니라 경고다. architecture.md:168의 "타입 위치" 아키텍처 테스트는 부재.
  부수로 무제한 정책(maxIssuance=null) 쿠폰이 관리자 표면에 issuedCount 0으로 표시되는 표시 정합.
- 완료 기준: architecture.md 아웃박스 문구를 실재("발행 포트")로 축소, NullAway/Error Prone 서술을
  실배선 기준으로 정정, "타입 위치"를 빼거나 규칙 추가 방향 명시. 어긋난 나머지 지점 정합. 코드 변경이
  얽히면(#14) 문서만 이 항목에서 처리하고 규칙 추가는 그쪽으로 미룬다.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 14. 모듈 경계·아키텍처 테스트 강제 구멍 보강

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: "빌드가 강제한다"고 문서화된 경계의 우회 구멍을 닫는다.
- 문제(Medium): (a) 모듈 경계 화이트리스트가 api/implementation만 순회해
  (ModuleDependencyRules.kt:12, convention.external-module.gradle.kts:18) compileOnly·testImplementation
  등으로 타 도메인 컴파일 의존이 가능하다 — "경계를 컴파일 의존성으로 강제"(docs/architecture.md:34)의
  구멍. (b) 엔티티 비노출 ArchUnit 규칙의 제외가 전역 패키지명 기준이라(ArchitectureTest.java:55)
  app-api에 ...service/...entity 패키지를 만들면 생 엔티티 참조가 면제된다.
- 완료 기준: 경계 검사 대상 구성을 compileOnly·compileOnlyApi·runtimeOnly·testImplementation까지 확대
  (또는 프로젝트 의존 전수 검사). ArchUnit 엔티티 제외를 도메인 모듈 접두로 한정. 각각 우회 시도가 실패로
  잡히는 테스트/검증을 추가.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 15. 멱등 필터 fail-closed 형상 통일

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 멱등 필터 Redis 장애 응답을 레이트리밋과 같은 503 problem+json으로 통일한다.
- 문제(Medium): IdempotencyFilter.java:44-52가 Redis 장애 시 예외를 잡지 않아 필터 밖으로 전파되고,
  ProblemDetailHandler가 DispatcherServlet 내부 예외만 처리해 problem+json이 아닌 일반 500이 된다.
  fail-closed(거부) 자체는 충족하나, 레이트리밋 필터가 명시적 503 problem+json을 내는 것과 형상이
  불일치한다.
- 완료 기준: 멱등 필터도 레이트리밋 필터처럼 저장소 예외를 잡아 503 problem+json으로 통일. Redis 장애 시
  503 응답 형상 테스트(#11의 fail-closed 테스트와 조율).
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 24. 취소×출고 경합 환불 고아 회복 경로 (번호는 등재 순서)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 취소×출고 경합이 남기는 환불 고아(결제 CANCELLED × 주문 PAID+SHIPPED)의 회복 경로를 구현한다.
- 문제(Medium, #3 독립 리뷰 발견): 취소 파사드가 결제 환불을 주문 전이 앞에 커밋하므로, 취소가 가드
  (PAID·PREPARING) 통과 → PG 환불·결제 CANCELLED 커밋 → 그 사이 ship이 주문 행을 선점 커밋 → 취소의
  주문 전이가 가드/낙관락으로 거부되면 결제 CANCELLED × 주문 PAID+SHIPPED(돈은 환불, 상품은 출고)가
  남는다. settleRecorded는 CANCELLED 결제 잔여를 취소 파사드 재시도 소유로 두는데, 재시도는 SHIPPED라
  ORDER_NOT_CANCELLABLE로 거부돼 자동 회복 경로가 없다.
- 결정: todo.md 선행 결정 절에서 확정된 방향(재청구/출고 차단/SHIPPED 취소 허용/운영 대사 명시 중 택1)을
  따른다. 확정 기록이 없으면 착수하지 않는다.
- 완료 기준: 확정된 방향의 구현(또는 문서 명시) + 취소×출고 경합 재현 IT.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

---

## 있으면 좋음

### 16. 리컨실 유예 관계 기동 검증

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 리컨실 유예 관계(order ≥ payment)를 기동 시 검증한다.
- 문제(Low): order.reconciliation.stale-after(15m) ≥ payment.reconciliation.stale-after(10m) 관계
  (application.yml)는 현재 값은 충족하나 이를 강제하는 코드가 없다. 설정 역전 시 "이중 개입 차단" 전제가
  조용히 깨진다(REQUIREMENTS.md:165).
- 완료 기준: 두 파사드 중 한쪽 생성자(또는 기동 검증)에서 관계를 assert. 역전 설정 시 기동 실패 테스트.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 17. 로그인 타이밍 오라클 완화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 로그인 실패의 타이밍 채널로 계정 존재가 유추되지 않게 한다.
- 문제(Low): MemberCredentialValidator.java:35-37이 미존재·탈퇴 이메일에서 bcrypt 비교 전에 즉시
  거부해, 응답 시간 차(bcrypt 유무)로 계정 존재를 유추할 수 있다. 응답 본문·상태는 동일하나 타이밍
  채널에서 "계정 존재 비노출" 목표를 위반한다(레이트리밋이 대량 측정은 늦춤).
- 결정(2026-07-16 확정): 더미 해시 등화 — 코드로 해소한다(문서 수용 기각).
- 완료 기준: 미존재/탈퇴 경로에서도 고정 더미 해시로 bcrypt를 한 번 태워 연산량을 동일화. 회귀 방지
  최소 검증.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 18. 가입 계정 존재 열거 완화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 공개 가입 표면의 계정-존재 열거를 완화한다.
- 문제(Low): 공개 가입이 활성 이메일 중복 시 409, 아니면 201을 내고, 레이트리밋이 로그인에만 붙어 가입은
  무제한이다(RateLimitConfig). 이메일을 바꿔가며 활성 회원을 열거할 수 있어 로그인 측 "존재 비노출"
  자세와 불일치한다.
- 결정(2026-07-16 확정): 가입 표면에 IP 스로틀을 추가한다(로그인과 같은 저장소·근거). 범위 밖 수용
  문서화는 기각.
- 완료 기준: 가입 표면 IP 스로틀 추가(로그인과 같은 저장소·근거). 스로틀 동작 검증.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 19. 입력 검증·표시 정합 번들

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 경미한 입력 검증·표시 정합을 묶어 처리한다(외과적으로).
- 문제(Low): (a) 옵션 정규화가 trim() 후 NFKC라 NBSP(U+00A0) 등이 시그니처에 잔류해 유니크를 우회할 수
  있다(NormalizedOptions.java:45-47). (b) ProductVariantAppender.java:58-61이 모든
  DataIntegrityViolationException을 DUPLICATE로 매핑해 option_signature 길이 초과가 "중복"으로 오보고
  된다(요청 DTO에 @Size 부재, OptionRequest.java:7). (c) CartItem.java:57-60의 수량 합산 int 오버플로
  무가드.
- 완료 기준: NFKC 정규화 후 strip() 순서로 변경, 옵션 필드에 @Size 추가(경계 검증 원칙 정합), 수량 합산
  상한 가드. 각 케이스 단위 테스트. 무관한 코드는 건드리지 않는다.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 20. 상품 상세 재고 배치 조회 (N+1 제거)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 상품 상세 합성의 변형당 재고 개별 조회를 배치로 바꾼다.
- 문제(Low): ProductDetailFacade.java:51-59가 변형마다 stockReader.getByVariantId를 호출한다(N 쿼리).
  배치 API getByVariantIds가 이미 있고 카탈로그 목록은 그걸 쓴다(목록은 N+1 없음). 상세만 이탈한다.
- 완료 기준: 상세도 getByVariantIds IN 배치로 치환. 변형 N개에 재고 쿼리 1회임을 검증(테스트 또는 쿼리
  카운트).
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 21. 시간 소스 Clock 주입 일관화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 시간 판정·기록 지점을 주입 Clock으로 일관화한다.
- 문제(Low): coupon만 Clock을 주입하고 나머지 엔티티는 Instant.now() 직접 호출이다(Member.java:103·
  Product.java:86·Order.java:171,187,204,213,223·Payment.java:95,102,123). coupon 내부도 혼용
  (IssuedCoupon.revoke가 Instant.now(), :109). 앱 전역 ClockConfig 빈이 있는데도 PendingOrderSweepFacade·
  PaymentConfirmationFacade가 Instant.now() 직접이고, JwtTokenCodec도 시각 판정 2곳이 직접이다.
  테스트 결정성·일관성이 떨어진다.
- 결정(2026-07-16 확정): 전면 일관화 — main 소스의 Instant.now() 직접 호출 전부(JwtTokenCodec 포함)를
  coupon 패턴으로 통일한다. 빈은 Clock 주입, 엔티티 메서드는 Instant now 파라미터.
- 완료 기준: main 소스의 Instant.now() 직접 호출 전부를 주입 Clock 경유로 일관화(coupon 패턴, 무관한
  스타일 변경 금지). 고정 시각 테스트 가능성 확보.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 22. QueryDSL 죽은 의존 정리

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 사용되지 않는 QueryDSL 배선을 정리하거나 사용 계획을 명시한다.
- 문제(Low): 컨벤션 플러그인이 전 도메인에 querydsl-jpa+APT를 배선해 Q클래스가 생성되나 src/main 전체
  에서 JPAQueryFactory/querydsl 사용처가 0건이다(전 리포지토리가 파생 쿼리+@Query로 끝남). 빌드 시간·
  의존만 지불 중이다.
- 결정(2026-07-16 확정): 제거한다. 유지+사용 계획 문서화는 기각.
- 완료 기준: 컨벤션 플러그인(convention.domain-module.gradle.kts)·버전 카탈로그에서 QueryDSL 배선을
  제거하고 전 모듈 빌드 정합 확인.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 23. 컨테이너·마이그레이션 운영 하드닝 번들

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 검증 기준을 합의한 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: 경미한 컨테이너·마이그레이션 운영 항목을 묶어 처리한다.
- 문제(Low): (a) app-migration에 구조화 로깅 설정이 없어 마이그레이션 로그가 plain text다(app-api는
  ECS). (b) Docker 헬스체크가 /dev/tcp 리슨만 확인해 DB/Redis 불능 상태를 healthy로 판정한다
  (docker-compose.yml:84-91, /actuator/health 미사용). (c) readiness 그룹에 DB/Redis 미포함(오케스트
  레이터 도입 시). (d) libs.versions.toml의 java="25"가 미소비이고 실제 버전이 컨벤션 플러그인·CI·
  Dockerfile에 리터럴로 산재한다(정본 드리프트 표면).
- 완료 기준: app-migration ECS 로깅 추가, 헬스체크를 HTTP GET 기반으로(또는 프로브 명시), readiness
  DB/Redis 정책 명문화, Java 버전 정본화(주석 참조라도). 이미지 빌드·기동 검증(#12 관측성과 조율).
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

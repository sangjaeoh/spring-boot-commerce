# TODO — 프로덕션 채택 갭 해소

출처: 독립 6관점 리뷰(체크아웃·결제 / 재고·쿠폰 동시성 / 회원·인증·보안 / 카트·상품·컨벤션 / 테스트 커버리지 / 빌드·운영), 2026-07-16. "조건부 채택" 판정의 근거가 된 결함·불일치를 작업 순서로 정리한다. 핵심 결함은 전진 경로가 아니라 **보상 경로(취소·환불·리컨실·스윕)의 정합**에 몰려 있다.

## 작업 규칙

- 각 항목은 하나의 슬라이스다. AGENTS.md·docs/의 규칙을 로딩하고 준수한다.
- 구현 전 검증 기준(완료 기준)을 합의하고, 슬라이스 머지 전 **구현과 분리된 독립 리뷰**를 수행한다.
- 완료 후 `./gradlew build` 게이트 통과 + `docs/architecture.md`의 "빌드가 강제하는 불변식" 목록으로 자기검증한다.
- 선행 결정은 전부 확정됐다(아래 "선행 결정" 참조). 제목에 `[결정]`이 붙은 항목은 본문 `결정` 라인을 따르고 되묻지 않는다.
- 각 항목은 `상태: 대기 | 보류(결정 대기) | 완료` 마커를 가진다. 자동 루프·새 세션은 위에서부터 첫 `대기` 항목만 수행한다.
- 세션별 착수 프롬프트는 `todo-prompt.md`가 소유한다(번호 일치). 자동 진행 규약은 `loop-prompt.md`가 소유한다.

## 선행 결정 (2026-07-16 확정)

리뷰 대상 저장소가 스스로 문서화한 원칙을 정본으로 삼아, 코드가 그 원칙을 어긴 지점을 원칙 쪽으로 수렴시키는 방향으로 확정한다. 재검토가 필요하면 해당 항목 착수 전에 이 절을 갱신한다.

- **복원 게이트 원칙**(#1): `DOMAIN_MODEL.md:689` "모든 재고·쿠폰 복원은 주문의 1회성 전이가 게이트한다"를 코드로 관철한다. (a) `restoreUse`에 orderId를 넘겨 사용-주문 일치일 때만 복원, (b) 이미 CANCELLED/REFUNDED인 관용 통과 경로는 복원을 재실행하지 않고 취소 전이가 이 호출에서 실제 일어났을 때만 복원을 태운다, (c) 체크아웃 동기 보상도 스윕·리컨실과 같은 취소-선행 → 복원-후행 순서로 통일한다.
- **낙관락 승격**(#3): `docs/entity-persistence.md:105` "경합 민감 상태전이만 `@Version` 승격"에 따라 주문의 취소·환불 상태 전이(및 결제 취소)를 경합 민감으로 인정하고 `@Version` + 409 매핑으로 승격한다. 재고와 일관된 낙관락+409를 기본으로 하되, 상태 조건부 UPDATE(`where status = 'PAID'`, 영향 행 0이면 복원 스킵) 대안도 허용한다.
- **리컨실 관할 확장**(#2): `REQUIREMENTS.md:149` "돈과 주문 상태가 어긋난 채 방치되지 않음"을 관철하기 위해 결제 리컨실 대상에 "비REQUESTED(APPROVED) 결제 × 주문 미종결/종결"을 포함한다 — APPROVED × PENDING은 `markPaid` 완결, APPROVED × 취소·환불은 고아 청구 환불.
- **PENDING 스윕 복원 방향**(#4): 과복원(재고 증식 → 오버셀)보다 과소복원(팬텀 품절)이 안전하다. 차감 완료 증거(주문 단위 차감 마커) 없이 재고를 복원하지 않고, 증거가 있는 라인만 복원한다.
- **HIDDEN 상품 상세 가시성**(#10): `DOMAIN_MODEL.md:125` "HIDDEN = 그룹 전체 노출·주문 불가"를 정본으로 본다. 공개 상세도 HIDDEN·삭제 상품을 404로 은닉하고, 노출을 의도로 고정한 기존 테스트를 정합한다.
- **로그인 타이밍 등화**(#17): 미존재·탈퇴 이메일 경로에도 고정 더미 해시로 bcrypt를 한 번 태워 연산량을 동일화한다. 문서 수용(한계 명시)은 기각했다.
- **가입 IP 스로틀**(#18): 공개 가입 표면에 로그인과 같은 저장소·근거의 IP 레이트리밋을 추가한다. 범위 밖 수용 문서화는 기각했다.
- **Clock 전면 일관화**(#21): main 소스의 `Instant.now()` 직접 호출 전부(`JwtTokenCodec`의 시각 판정 2곳 포함)를 coupon 선례로 통일한다 — 빈은 `Clock` 주입, 엔티티 메서드는 `Instant now` 파라미터. 부분 일관화(빈 계층만)는 표준이 두 개인 상태를 남겨 기각했다.
- **QueryDSL 제거**(#22): 미사용 querydsl-jpa+APT 배선을 컨벤션 플러그인·버전 카탈로그에서 제거한다. 유지+사용 계획 문서화는 기각했다.

---

## 반드시 (출시 차단)

### 1. 취소·환불 복원 exactly-once 게이트 [결정]
- 상태: 완료
- 결정(2026-07-16): 복원 게이트 원칙 확정(위 선행 결정). `restoreUse(orderId)` 일치 + 관용 통과 시 복원 무재실행 + 보상 순서 통일.
- 문제(Critical): 완결된 취소·환불을 `Idempotency-Key` 헤더 없이 재호출하면 복원이 무게이트로 재실행된다. `OrderCancellationFacade.java:80-83`이 이미 CANCELLED인 주문을 관용 통과시킨 뒤 `:61-77`이 전이 발생 여부와 무관하게 전 라인 재고 `restore`(가산, `Stock.java:74-77`) + 쿠폰 `restoreUse`를 재실행 → 재고가 호출 횟수만큼 증식. 더 심각하게, `IssuedCoupon.restoreUse()`(`IssuedCoupon.java:114`)가 orderId를 검증하지 않아, 주문 A 취소 완결 후 그 쿠폰을 주문 B에 재사용(USED)한 상태에서 주문 A 취소를 재호출하면 **B가 쓰는 쿠폰이 ISSUED로 풀린다**(쿠폰 재장전, 할인 반복 수취). `OrderRefundFacade.java:76-84`도 동일 패턴(관리자 한정이라 악용면만 좁음). `REQUIREMENTS.md:145` "정확히 한 번만 복원" 위반이며 `DOMAIN_MODEL.md:517`("전이가 실제 일어난 호출에서만 보상")과 :628이 문서 내부에서 상충한다.
- 완료 기준: `restoreUse(issuedCouponId, orderId)`로 시그니처를 바꿔 `status == USED && orderId 일치`일 때만 복원(전 호출부 5곳 `CheckoutFacade`·`OrderCancellationFacade`·`OrderRefundFacade`·`PendingOrderSweepFacade`·`PaymentConfirmationFacade` 정합). 관용 통과 경로는 복원을 재실행하지 않도록, 취소 전이가 이 호출에서 실제 일어났는지로 복원을 게이트. 체크아웃 보상을 취소-선행 순서로 통일. IT: 취소 완결 후 재호출이 재고·쿠폰을 추가로 건드리지 않음 + 크로스-주문 쿠폰 재장전 차단.
- 범위: 중~대.
- 이 항목을 1번에 두는 이유: 유일한 Critical이고 이후 보상 경로 항목(#2·#3·#4)의 순서·게이트 전제를 세운다.

### 2. APPROVED 결제 리컨실 사각지대 봉합 [결정]
- 상태: 완료
- 결정(2026-07-16): 리컨실 관할 확장 확정(위 선행 결정).
- 문제(High): `PaymentProcessor.approve`가 APPROVED를 자기 트랜잭션으로 먼저 커밋하고(`PaymentProcessor.java:62-63`) `markPaid`는 별도 트랜잭션·try 밖이다(`CheckoutFacade.java:119-120`). 둘 사이 크래시 또는 `markPaid` 예외 시 payment=APPROVED, order=PENDING이 남는데 결제 리컨실은 REQUESTED만 스윕하고 PENDING 스윕은 payment 행이 있으면 위임한다(`PendingOrderSweepFacade.java:91-94`) — 위임받을 쪽이 이 결제를 영영 안 본다. 돈은 빠졌는데 주문 영구 PENDING, 로그·알람 없음. 리컨실 고아 환불 분기도 승인 기록 커밋 후 환불을 호출해(`PaymentConfirmationFacade.java:135-138`) 환불 실패 시 APPROVED로 굳어 스윕 대상에서 이탈한다(자기복구 상실). `REQUIREMENTS.md:149`·`DOMAIN_MODEL.md:687`("잔여는 스윕 주기 안에 수렴") 위반.
- 완료 기준: 결제 리컨실이 "APPROVED × 주문 PENDING → `markPaid` 완결", "APPROVED × 취소·환불 주문 → 고아 청구 환불"을 처리한다. 고아 환불은 승인 기록과 환불을 자기복구 가능한 순서(환불 먼저 또는 한 커밋)로 재배치. 크래시 주입 IT로 두 사각지대가 다음 스윕에 수렴함을 검증.
- 진행 메모(#1 독립 리뷰 발견, 2026-07-16): FAILED 결제 × PENDING 주문(체크아웃 거절 기록 후 보상 취소 실패 잔여)도 동일 무관할 — 스윕은 payment 행 존재로 제외, 리컨실은 REQUESTED만 선별. 관할 확장 시 "FAILED × PENDING → 취소 전이 후 복원(보상 종결)"을 함께 다룰지 판단하고, 채택 여부와 무관하게 DOMAIN_MODEL.md 체크아웃 정책의 잔여 서술(현재 무관할 유실로 명시)을 결과에 맞게 갱신한다.
- 범위: 중.

### 3. 주문·결제 생명주기 낙관락 [결정]
- 상태: 완료
- 결정(2026-07-16): 낙관락 승격 확정(위 선행 결정).
- 문제(High): `Order`·`Payment` 어디에도 `@Version`이 없다. 취소 전이 가드는 load-check-write라, 동시 취소 2건이 겹치면 둘 다 PAID/APPROVED를 읽고 가드를 통과해 last-write-wins로 둘 다 커밋 → 두 스레드 모두 재고·쿠폰 복원 루프를 실행(이중 가산). PG 실환불만 결정론적 멱등 키가 방어한다. `DOMAIN_MODEL.md:563`("결제는 동시 경합이 없다")·:689("전이 가드가 정확히-1회 복원 보장")·`REQUIREMENTS.md:145`("1회성 전이가 구조적으로 거부")가 거짓 전제가 된다.
- 완료 기준: 주문의 상태 전이(및 결제 취소)를 낙관락으로 직렬화(`@Version` + Flyway version 컬럼 + `ddl-auto=validate` 정합, 또는 상태 조건부 UPDATE). `ObjectOptimisticLockingFailureException` → 409 매핑(`docs/entity-persistence.md:107`). 동시 취소 2건 IT로 복원이 정확히 한 번임을 검증.
- 범위: 중.

### 4. PENDING 스윕 재고 복원 안전화 [결정]
- 상태: 완료
- 결정(2026-07-16): PENDING 스윕 복원 방향 확정(위 선행 결정) — 과소복원(팬텀 품절) 방향.
- 문제(High): `PendingOrderSweepFacade.java:99-106`이 차감 진행도를 모른 채 전 라인을 무조건 `restore`한다. 체크아웃이 주문 PENDING 커밋 직후(차감 전) 또는 라인 j/n 차감 중 크래시하면 차감된 적 없는 라인까지 +qty → 실물보다 부푼 재고 → 오버셀. 동기 보상은 `deducted` 리스트로 실제 차감분만 복원하는데(`CheckoutFacade.java:220-231`) 스윕만 이 정밀도가 없다. `DOMAIN_MODEL.md:658`은 잔존을 "차감 재고가 남은" 상태로만 가정해 place 커밋~차감 완료 사이 크래시를 가정 밖에 둔다.
- 완료 기준: 주문 단위 차감 완료 증거(마커)를 남기고, 스윕은 증거 있는 라인만 복원한다(증거 없으면 복원 생략 → 팬텀 품절, 운영 대사로 강등). 크래시 타이밍별 IT로 재고 증식이 없음을 검증. 문서에 채택한 잔여 방향을 명시.
- 범위: 중.

### 5. 장바구니 쓰기 경로 동시성 견고화
- 상태: 완료
- 문제(High): `CartAppender.java:22-24`의 get-or-create가 무방비다. 동시 첫 담기 2건 → 둘 다 `findByMemberId` 빈 결과 → 둘 다 `save` → `cart.member_id` 유니크 위반 → 어떤 핸들러도 안 잡아 **500**. 동일 변형 동시 담기 → `cart_item(cart_id,variant_id)` 유니크 위반 → 500. 라인 있는 동시 합산은 `CartItem.java:57-60`의 메모리 read-modify-write + `@Version` 부재라 합산이 소실(qty 1+1이 3 대신 조용히 잘못된 값). product·stock Appender는 동일 경합을 `saveAndFlush`+catch로 방어하는데 cart만 이탈한다(유일하게 실결함으로 이어진 비일관성).
- 완료 기준: 유니크 위반 catch 후 재조회-재시도(get-or-create), 수량 합산을 원자적(`@Version` 또는 조건부 UPDATE)으로. 동시 담기 IT로 500 부재 + 합산 정확성 검증. product/stock의 기존 이중화 패턴과 정합.
- 범위: 소~중.

### 6. 상품 등록 중단-재개 로직
- 상태: 완료
- 완료 메모(2026-07-16): 채택 의미론 — 동일 옵션 DISABLED 변형은 재개(재고 확인→create→enable, 가격·기존 재고 유지), ACTIVE는 중복 409 유지, RETIRED는 신규 생성 유지. 부수 효과로 관리자가 disable한 동일 옵션 변형에 addVariant를 부르면 재활성화된다(시딩 중단과 구분할 영속 증거가 없어 재개 우선 — DOMAIN_MODEL 등록 절에 문서화). 이 의미론이 부적절하면 재검토 항목으로 등재할 것. registerProduct 자체의 중단(상품 중복 생성)은 범위 밖 잔여.
- 문제(High): `ProductRegistrationFacade.addVariant`가 무조건 변형 `create`부터 시작한다(`ProductRegistrationFacade.java:55-60`). 변형 create 성공 후 재고 create 전에 중단되면, 재시도가 비-RETIRED 중복 검사(`ProductVariantAppender.java:50-53`)에 걸려 409로 영구 차단되고, 재고 시딩용 관리자 API도 없어(`StockController`에 create 엔드포인트 부재) retire 후 재등록 외엔 복구 불가. `DOMAIN_MODEL.md:673` "중단 복구는 남은 단계를 재개한다(재생성이 아니라 재개)"를 위반한다.
- 완료 기준: `addVariant`가 동일 시그니처의 비-RETIRED 변형이 이미 있으면 그 변형으로 남은 단계(재고 존재 확인 → create → enable)를 이어가는 재개 분기를 갖는다. 중단 지점별(변형만/재고 전/미활성) IT로 재개가 성공함을 검증.
- 범위: 중.

---

## 권장

### 7. 보상 복원 유실 트리거 축소
- 상태: 완료
- 문제(Medium): 리컨실 거절 보상(`PaymentConfirmationFacade.java:148-157`)과 PENDING 스윕(`PendingOrderSweepFacade.java:99-106`)의 복원 루프가, 취소 전이 커밋 후 라인 복원 중 **일시 예외(동시 체크아웃과의 재고 낙관락 충돌 등)** 하나면 끊기고 다음 스윕은 주문이 이미 CANCELLED라 복원 분기를 건너뛴다 → 남은 라인 복원이 영구 유실. `DOMAIN_MODEL.md:653`은 이를 "중단(크래시)"으로만 서술해 발생 빈도를 과소 표현한다.
- 완료 기준: 복원 루프에 라인 단위 재시도를 넣는다(`restore`는 가산·교환법칙이라 재시도 안전 — `DOMAIN_MODEL.md:689`가 근거 제공). 일시 충돌 주입 IT로 남은 라인이 결국 복원됨을 검증. 문서의 잔여 서술을 실제 트리거 범위로 정정.
- 범위: 소~중.

### 8. 쿠폰 use 도메인 계약 소유 검증
- 상태: 완료
- 문제(Medium): `IssuedCouponModifier.use`가 `findById`로 로드해 소유(memberId)·산출할인>0을 검증하지 않는다(`IssuedCouponModifier.java:33-36`, 엔티티는 상태·기한만 가드 `IssuedCoupon.java:87-97`). `DOMAIN_MODEL.md:415`는 use 거부에 "미존재(미소유 포함)"를 명시한다. 현 유일 호출 경로는 파사드 preview가 `findByIdAndMemberId`로 선방어하나, 미래 호출자(신규 파사드·배치)가 타인 쿠폰을 사용 처리할 수 있다.
- 완료 기준: `use(issuedCouponId, memberId, orderId)`로 소유를 도메인에서 강제(`findByIdAndMemberId`). 타인 쿠폰 use가 미존재로 거부됨을 단위/IT로 검증.
- 범위: 소.

### 9. 장바구니 뷰 판매성 파생 반영
- 상태: 완료
- 문제(Medium): `CartViewFacade.java:37-56`이 변형 상태·상품 상태·재고를 전혀 읽지 않아, DISABLED/RETIRED 변형·HIDDEN/삭제 상품 라인이 정상가·소계로 표시되고 총액에도 합산된다(체크아웃에서야 거부). `DOMAIN_MODEL.md:135` "장바구니 표시는 카탈로그와 같은 파생을 읽기로 반영한다"를 위반한다.
- 완료 기준: 라인별 orderable/unavailable 파생 플래그를 카탈로그와 같은 합성(배치 조회)으로 추가. 뷰 IT로 비활성·삭제 라인이 unavailable로 표시됨을 검증. N+1 없이(IN 배치).
- 범위: 소~중.

### 10. 공개 상품 상세 HIDDEN 가시성 [결정]
- 상태: 완료
- 결정(2026-07-16): 문서 우선 — 공개 상세는 HIDDEN·삭제 상품을 404로 은닉(위 선행 결정).
- 문제(Medium): 공개 상세(`ProductController.java:112-115`, 게이트 없음)가 `deletedAt`만 필터하고(`ProductDetailFacade.java:46-47`) HIDDEN 상품을 노출한다. 비로그인 사용자가 ID만 알면 관리자가 일시 중지한 상품의 이름·설명·가격을 조회할 수 있다. 기존 테스트가 이 노출을 의도로 고정해 스펙-구현 충돌 상태다.
- 완료 기준: 공개 상세에서 HIDDEN·삭제 상품을 404로 은닉(카탈로그 목록과 동일 파생). 노출을 고정한 기존 테스트를 404 기대로 정합. 관리자 표면은 숨김 포함 조회 유지.
- 범위: 소.

### 11. 핵심 보상·경합 테스트 공백 보강
- 상태: 완료
- 문제(Medium): 실 인프라 위 466개 테스트에도 최악 시나리오 4건이 코드 배치로만 존재한다 — (a) 쿠폰 이중 사용 실경합(같은 쿠폰 × 동시 두 체크아웃 → 한쪽만): `IssuedCoupon.@Version`이 유일 방어인데 실경합 미검증. (b) PG 환불 실패 시 주문 PAID 유지·복원 없음: `paymentGateway.cancel`에 예외 주입 테스트 부재(취소·환불 어느 쪽에도). (c) 완전 성공 후 중복 환불의 재고 축: `OrderRefundFacadeTest`가 두 번째 refund를 실행하고도 재고를 안 봐(실제 +1 재가산 발생) 경계를 특성화하지 않음. (d) 멱등 필터 Redis 장애 거동 미검증. 부수로 다중 라인 주문 보상 시나리오 전무(전부 단일 라인).
- 완료 기준: (a) `CheckoutConcurrencyTest` 패턴 재사용한 쿠폰 이중 사용 경합 테스트, (b) PG 환불 실패 주입으로 PAID 유지·복원 없음 검증, (c) 중복 취소·환불 후 재고 상태 특성화(#1 수정 후 "추가 복원 없음"으로), (d) 멱등 필터 Redis 장애 fail-closed 테스트, (e) 다중 라인 보상 IT 최소 1건. #1·#3 구현 후 회귀 고정 역할.
- 진행 메모(#3 독립 리뷰 인계, 2026-07-16): 동시 환불 2건 경합 IT를 (a)와 같은 패턴으로 추가한다 — #3은 동시 취소만 IT로 고정했고 환불은 동일 메커니즘이라 유추 증거뿐이다. #2의 "고아 환불 성공 후 한 커밋 영속 실패 → 재시도 시 PG 이중 환불 차단" 지점도 IT 미특성화라 함께 다룬다.
- 범위: 중.

### 12. 관측성 최소셋 (메트릭 + 요청 상관관계 ID)
- 상태: 완료
- 완료 메모(2026-07-16): 배포 전제 — 인그레스가 `/actuator/**`를 외부로 라우팅하지 않아야 한다(메트릭 무인증 노출은 내부망 전제, #23 헬스체크 정비와 조율). app-migration ECS 로깅은 여기서 정합했다(#23 완료 기준의 해당 항목 선완료).
- 문제(Medium): actuator는 health만 노출하고 Micrometer 레지스트리·`traceId`/`X-Request-Id`/MDC·요청 로깅·에러 추적이 전무하다(grep 0건). #2·#4·#7 같은 "조용한 잔존·보상 실패"를 감지할 수단이 없어 정합 결함과 상보적 리스크다. `OrderPaidListener`처럼 삼킨 예외도 `log.warn`뿐.
- 완료 기준: Micrometer 메트릭 노출(커넥션 풀·스윕 처리 건수·409 비율 등 최소셋) + 요청 상관관계 ID(MDC) 배선 + 보상/스윕 실패 경고 로그. app-migration 포함 구조화 로깅 정합(#23과 조율). PII·시크릿 미노출 유지.
- 범위: 소~중.

### 13. 문서-코드 정합 (아웃박스 과잉 주장·품질 게이트 서술)
- 상태: 완료
- 완료 메모(2026-07-16): 부수 항목(무제한 정책 쿠폰의 관리자 issuedCount 0 표시)은 제품 결정이 필요해 미수정 보고로 남겼다 — 선택지: (i) 무제한이면 null 표시(응답 계약 변경, 권장 최소안), (ii) 실발급 총계 표시(목록 N+1 비용), (iii) 0 유지+문서화. 결정 후 소 슬라이스로 처리할 것.
- 문제(Medium): `docs/architecture.md:99`가 common-messaging을 "발행 포트·아웃박스·멱등 소비 지원"이라 적지만 실제는 마커 인터페이스+포트 1개(아웃박스는 `DOMAIN_MODEL.md:767`이 범위 밖으로 선언). `docs/code-quality.md:25-26`의 NullAway "@NullMarked 범위에서만 검사" 서술이 실배선(`AnnotatedPackages=com.commerce` 전역 검사)과 다르고, `:38`의 "Error Prone이 Javadoc 구조를 컴파일 시점에 강제"는 게이트가 아니라 경고. `architecture.md:168`의 "타입 위치" 아키텍처 테스트는 부재. 부수로 무제한 정책(maxIssuance=null) 쿠폰이 관리자 표면에 issuedCount 0으로 표시되는 표시 정합.
- 완료 기준: architecture.md 아웃박스 문구를 실재("발행 포트")로 축소, NullAway/Error Prone 서술을 실배선 기준으로 정정, "타입 위치"를 빼거나 규칙 추가 방향 명시. 문서-코드가 어긋난 나머지 지점 정합.
- 범위: 소.

### 14. 모듈 경계·아키텍처 테스트 강제 구멍 보강
- 상태: 완료
- 완료 메모(2026-07-17): 경계 검사는 선언된 프로젝트 의존 전수(`configurations.configureEach`)를 잡는다 — `dependencySubstitution`·`files(...)` 직접 참조 같은 저수준 우회는 보장 밖(독립 리뷰 참고, 비현실 경로로 범위 밖 판단). build-logic 테스트는 루트 `check`에 연결해 `./gradlew build` 게이트에 포함했다. ArchUnit 면제 베이스는 도메인 7개 + common-jpa(`@MappedSuperclass` 소유)로 파생·가드된다.
- 문제(Medium): (a) 모듈 경계 화이트리스트가 `api`/`implementation`만 순회해(`ModuleDependencyRules.kt:12`, `convention.external-module.gradle.kts:18`) `compileOnly`·`testImplementation` 등으로 타 도메인 컴파일 의존이 가능 — "경계를 컴파일 의존성으로 강제"(`docs/architecture.md:34`)의 구멍. (b) 엔티티 비노출 ArchUnit 규칙의 제외가 전역 패키지명 기준이라(`ArchitectureTest.java:55`) app-api에 `...service`/`...entity` 패키지를 만들면 생 엔티티 참조가 면제된다.
- 완료 기준: 경계 검사 대상 구성을 `compileOnly`·`compileOnlyApi`·`runtimeOnly`·`testImplementation`까지 확대(또는 프로젝트 의존 전수 검사). ArchUnit 엔티티 제외를 도메인 모듈 접두로 한정. 각각 우회 시도가 실패로 잡히는 테스트/검증 추가.
- 범위: 소~중.

### 15. 멱등 필터 fail-closed 형상 통일
- 상태: 완료
- 완료 메모(2026-07-17): 503 형상은 레이트리밋 `writeProblem` 미러(409 본문 불변, `IDEMPOTENCY_UNAVAILABLE`). 잔여 — `finally`의 `store.complete(key)` 장애는 응답 커밋 후 클린업이라 503 전환 불가·예외 전파 유지(원문이 지목한 `tryBegin` 결정 지점 아님, 필요 시 별도 항목).
- 문제(Medium): `IdempotencyFilter.java:44-52`가 Redis 장애 시 예외를 잡지 않아 필터 밖으로 전파되고, `ProblemDetailHandler`가 DispatcherServlet 내부 예외만 처리해 problem+json이 아닌 일반 500이 된다. fail-closed(거부) 자체는 충족하나, 레이트리밋 필터가 명시적 503 problem+json을 내는 것과 형상이 불일치한다.
- 완료 기준: 멱등 필터도 레이트리밋 필터처럼 저장소 예외를 잡아 503 problem+json으로 통일. Redis 장애 시 503 응답 형상 테스트(#11의 fail-closed 테스트와 조율).
- 범위: 소.

### 24. 취소×출고 경합 환불 고아 회복 경로 [결정]
- 상태: 보류(결정 대기)
- 보류 사유(2026-07-17): 선행 결정 절에 회복 방향(재청구/출고 차단/SHIPPED 취소 허용/운영 대사 중 택1) 확정 기록이 없다. 본문 "확정 전 착수 금지"에 따라 보류한다. 결정 확정 시 선행 결정 절에 기록 후 상태를 대기로 되돌릴 것.
- 출처: #3 독립 리뷰 발견(2026-07-16). 번호는 등재 순서라 섹션 순서와 다르다.
- 문제(Medium, 선재 갭): 취소 파사드가 결제 환불을 주문 전이 앞에 커밋하므로, 취소가 가드(PAID·PREPARING) 통과 → PG 환불·결제 CANCELLED 커밋 → 그 사이 `ship`이 주문 행을 선점 커밋 → 취소의 주문 전이가 가드/낙관락으로 거부되면 **결제 CANCELLED × 주문 PAID+SHIPPED**(돈은 환불, 상품은 출고)가 남는다. `PaymentConfirmationFacade.settleRecorded`는 CANCELLED 결제 잔여를 취소 파사드 재시도 소유로 두는데, 재시도는 SHIPPED라 `ORDER_NOT_CANCELLABLE`로 거부돼 자동 회복 경로가 없다. #3(낙관락) 이전부터 있던 창이며 낙관락은 경합 결과를 결정론화했을 뿐이다.
- 결정 필요: 회복 방향 — 재청구(PG 모델상 환불 취소 없음), 출고 차단(취소 개시 마커로 ship 거부), SHIPPED 취소 허용(정책 변경), 운영 대사 명시 중 택1. 확정 전 착수 금지.
- 완료 기준: 확정된 방향의 구현(또는 문서 명시) + 취소×출고 경합 재현 IT.
- 범위: 소~중.

---

## 있으면 좋음

### 16. 리컨실 유예 관계 기동 검증
- 상태: 완료
- 완료 메모(2026-07-17): 검증 지점은 `PendingOrderSweepFacade` 생성자(관계의 소비자, 두 값을 아는 기존 자리) — 역전 시 IAE로 빈 배선 실패 = 기동 실패. 역전 거부·경계 동등·초과 수용 단위 테스트로 고정.
- 문제(Low): `order.reconciliation.stale-after`(15m) ≥ `payment.reconciliation.stale-after`(10m) 관계(`application.yml`)는 현재 값은 충족하나 이를 강제하는 코드가 없다. 설정 역전 시 "이중 개입 차단" 전제가 조용히 깨진다(`REQUIREMENTS.md:165`).
- 완료 기준: 두 파사드 중 한쪽 생성자(또는 기동 검증)에서 관계를 assert. 역전 설정 시 기동 실패 테스트.
- 범위: 소.

### 17. 로그인 타이밍 오라클 완화 [결정]
- 상태: 완료
- 완료 메모(2026-07-17): 더미 해시는 기동 시 같은 인코더로 생성(코스트 동일, 하드코딩 없음). 인증 성공은 존재+일치 동시 요구로 더미 일치가 인증될 수 없다. 형식 오류 이메일의 bcrypt 선차단(선재 fast path)은 계정 존재 오라클이 아니라 범위 밖 유지.
- 결정(2026-07-16): 더미 해시 등화 확정(위 선행 결정) — 코드로 해소, 문서 수용 기각.
- 문제(Low): `MemberCredentialValidator.java:35-37`이 미존재·탈퇴 이메일에서 bcrypt 비교 전에 즉시 거부해, 응답 시간 차(bcrypt 유무)로 계정 존재를 유추할 수 있다. 응답 본문·상태는 동일하나 타이밍 채널에서 "계정 존재 비노출" 목표를 위반한다(레이트리밋이 대량 측정은 늦춤).
- 완료 기준: 미존재/탈퇴 경로에서도 고정 더미 해시로 bcrypt를 한 번 태워 연산량을 동일화. 회귀 방지 최소 검증.
- 범위: 소.

### 18. 가입 계정 존재 열거 완화 [결정]
- 상태: 완료
- 완료 메모(2026-07-17): 같은 필터·스토어를 `RateLimitScope`(키 접두사 `login:`/`signup:`·거부 문구)로 재사용, 한도·창은 로그인과 동일 상수 공유. 독립 리뷰로 필터를 POST 한정 게이트 — 같은 경로의 관리자 GET 이메일 검색이 가입 버킷을 소모하던 부수효과 제거. 잔여 — `LoginRateLimit*` 명칭이 이제 가입도 덮어 레거시(범용 리네임은 별도 슬라이스).
- 결정(2026-07-16): 가입 IP 스로틀 확정(위 선행 결정) — 범위 밖 수용 문서화 기각.
- 문제(Low): 공개 가입이 활성 이메일 중복 시 409, 아니면 201을 내고 레이트리밋이 로그인에만 붙어 가입은 무제한이다(`RateLimitConfig`). 이메일을 바꿔가며 활성 회원을 열거할 수 있어 로그인 측 "존재 비노출" 자세와 불일치.
- 완료 기준: 가입 표면에 IP 스로틀 추가(로그인과 같은 저장소·근거). 스로틀 동작 검증.
- 범위: 소.

### 19. 입력 검증·표시 정합 번들
- 상태: 완료
- 완료 메모(2026-07-17): options 목록에도 `@Size(max=10)`을 추가했다 — 조합 길이는 목록 상한 없이는 보장 불가(시그니처 819≤1000·라벨 427≤500). 잔여 — (i) 병리적 NFKC 팽창 문자(합자 등)는 여전히 DB 백스톱에 걸려 409로 오보고될 수 있음(도메인 측 정규화 후 길이 검사로 완전 봉합 가능, 현실 위협 아님), (ii) 표시 라벨은 trim() 유지라 가장자리 NBSP 잔류(표시 전용, 유니크 무관).
- 문제(Low): (a) 옵션 정규화가 `trim()` 후 NFKC라 NBSP(U+00A0) 등이 시그니처에 잔류해 유니크를 우회할 수 있다(`NormalizedOptions.java:45-47`). (b) `ProductVariantAppender.java:58-61`이 모든 `DataIntegrityViolationException`을 DUPLICATE로 매핑해 `option_signature` 길이 초과가 "중복"으로 오보고(요청 DTO에 `@Size` 부재, `OptionRequest.java:7`). (c) `CartItem.java:57-60`의 수량 합산 int 오버플로 무가드.
- 완료 기준: NFKC 정규화 후 `strip()` 순서로 변경, 옵션 필드에 `@Size` 추가(경계 검증 원칙 정합), 수량 합산 상한 가드. 각 케이스 단위 테스트.
- 범위: 소.

### 20. 상품 상세 재고 배치 조회 (N+1 제거)
- 상태: 완료
- 완료 메모(2026-07-17): 카탈로그와 같은 `orderableVariantIds` 헬퍼 형태로 정합(미존재 재고 = 집합 부재 = orderable=false, 기존 예외 catch 의미론 동일). 배치 1회·단건 0회를 스파이 verify로 고정.
- 문제(Low): `ProductDetailFacade.java:51-59`가 변형마다 `stockReader.getByVariantId`를 호출한다(N 쿼리). 배치 API `getByVariantIds`가 이미 있고 카탈로그 목록은 그걸 쓴다(목록은 N+1 없음). 상세만 이탈.
- 완료 기준: 상세도 `getByVariantIds` IN 배치로 치환. 변형 N개에 재고 쿼리 1회임을 검증.
- 범위: 소.

### 21. 시간 소스 Clock 주입 일관화 [결정]
- 상태: 완료
- 완료 메모(2026-07-17): main의 `Instant.now()` 0건. 엔티티 12개 전이 메서드는 `Instant now` 파라미터, 서비스·파사드·`JwtTokenCodec`은 Clock 주입. member·order·payment·product 영속 테스트 컨텍스트에 coupon과 같은 고정 Clock 빈 추가, JWT 만료 고정 시각 결정론 테스트 추가. 범위 밖 잔여 — `Order` 주문번호 생성의 `System.currentTimeMillis()`·`UuidV7Generator`의 타임스탬프(선재, 시각 판정 아님).
- 결정(2026-07-16): 전면 일관화 확정(위 선행 결정) — 빈은 `Clock` 주입, 엔티티 메서드는 `Instant now` 파라미터(coupon 패턴), `JwtTokenCodec` 포함.
- 문제(Low): coupon만 `Clock`을 주입하고 나머지 엔티티는 `Instant.now()` 직접 호출이다(`Member.java:103`·`Product.java:86`·`Order.java:171,187,204,213,223`·`Payment.java:95,102,123`). coupon 내부도 혼용(`IssuedCoupon.revoke`가 `Instant.now()`, `:109`). 앱 전역 `ClockConfig` 빈이 있는데도 `PendingOrderSweepFacade`·`PaymentConfirmationFacade`가 `Instant.now()` 직접, `JwtTokenCodec`도 시각 판정 2곳 직접. 테스트 결정성·일관성 저하.
- 완료 기준: main 소스의 `Instant.now()` 직접 호출 전부를 주입 `Clock` 경유로 일관화(coupon 패턴, 무관한 스타일 변경 금지). 고정 시각 테스트 가능성 확보.
- 범위: 소~중(전면 일관화 확정으로 엔티티 시그니처 파급을 포함해 상향).

### 22. QueryDSL 죽은 의존 정리 [결정]
- 상태: 완료
- 완료 메모(2026-07-17): common-jpa의 배선(QBaseTimeEntity 생성용)과, 유일 annotationProcessor 제거로 고아가 된 java-base의 생성 코드 정적분석 예외 2줄도 함께 제거했다. architecture.md 조회 순서는 "동적 조회 도구 미배선 — 필요 시 재도입 결정"으로 정합(사다리 계단을 조용히 삭제하지 않음).
- 결정(2026-07-16): 제거 확정(위 선행 결정) — 유지+계획 문서화 기각.
- 문제(Low): 컨벤션 플러그인이 전 도메인에 querydsl-jpa+APT를 배선해 Q클래스가 생성되나 `src/main` 전체에서 `JPAQueryFactory`/querydsl 사용처 0건이다(전 리포지토리가 파생 쿼리+`@Query`로 끝남). 빌드 시간·의존만 지불 중.
- 완료 기준: 컨벤션 플러그인(`convention.domain-module.gradle.kts`)·버전 카탈로그에서 QueryDSL 배선을 제거하고 전 모듈 빌드 정합 확인.
- 범위: 소.

### 23. 컨테이너·마이그레이션 운영 하드닝 번들
- 상태: 완료
- 완료 메모(2026-07-17): (a) app-migration ECS 로깅은 #12 선완료 확인(이미지 로그 실측). (b) 헬스체크는 JRE 이미지에 curl/wget이 없어 bash `/dev/tcp` HTTP GET으로 readiness 확인 — Redis 정지 시 503 전환·복구를 라이브 검증. (c) readiness 그룹 db·redis 포함, liveness는 프로세스 생존만 — 정책은 REQUIREMENTS.md 제약·전제. (d) 툴체인이 카탈로그 java를 실소비, CI·Dockerfile은 정본 참조 주석. 잔여 — DB/Redis 일시 장애 시 컨테이너가 unhealthy로 표시되나 compose restart 정책은 종료 기반이라 자동 재기동은 아님(오케스트레이터 도입 시 readiness probe로 그대로 매핑).
- 문제(Low): (a) app-migration에 구조화 로깅 설정이 없어 마이그레이션 로그가 plain text(app-api는 ECS). (b) Docker 헬스체크가 `/dev/tcp` 리슨만 확인해 DB/Redis 불능 상태를 healthy로 판정(`docker-compose.yml:84-91`, `/actuator/health` 미사용). (c) readiness 그룹에 DB/Redis 미포함(오케스트레이터 도입 시). (d) `libs.versions.toml`의 `java = "25"`가 미소비이고 실제 버전이 컨벤션 플러그인·CI·Dockerfile에 리터럴로 산재(정본 드리프트 표면).
- 완료 기준: app-migration ECS 로깅 추가, 헬스체크를 HTTP GET 기반으로(또는 프로브 명시), readiness DB/Redis 정책 명문화, Java 버전 정본화(주석 참조라도). 이미지 빌드·기동 검증.
- 범위: 소.

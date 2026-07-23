# Order 애그리거트 분해 설계 — 이행축 분리·잔여 3축 내부 재편

## 배경

`Order.java`가 731줄로 도메인 모듈 전체에서 두 번째로 큰 엔티티(`Payment.java` 250줄)의 3배에 가깝다. 결제·이행·취소·부분취소·반품 5축 상태가 한 클래스에 공존하며, 교차축 가드 조건이 많아 `OrderTest.java`도 801줄까지 부풀었다. 실사고(낙관락 409 폭주 등)는 관측되지 않았고, 코드 크기·테스트 비대화를 근거로 한 선제적 정리다.

축별 결합도를 분석한 결과 5축이 균질하지 않다.

- 결제·부분취소·반품 3축은 `computeLineRefund`가 `payAmount`·`discountAmount`·`totalAmount`·`refundedAmount`를 직접 참조하고 `OrderLine`을 공유해, 진짜로 분리하면 금액 필드를 이중보관하거나 애그리거트 간 동기 갱신이 필요해진다. `architecture.md`의 "애그리거트 간 참조는 ID만"·"트랜잭션당 애그리거트 1개 변경" 규칙과 충돌하거나 이중환불 위험을 만든다.
- 이행축(`fulfillmentStatus`·`shippedAt`·`carrier`·`trackingNumber`·`deliveredAt`·`holdReason`)은 금액 불변식과 무관하고, 다른 축의 상태를 읽기만 하고 쓰지 않는다 — 독립된 애그리거트로 분리 가능하다.

## 목표

- `Order.java`·`OrderTest.java`의 크기·교차축 결합을 줄인다.
- 이행축을 별도 애그리거트(`Fulfillment`)로 분리해, 이행 관련 쓰기가 결제·취소·반품 축과 낙관락 버전을 공유하지 않게 한다.
- 잔여 3축(결제·부분취소는 그대로, 취소·반품은 값 객체로) 내부 구조를 정리한다.
- `OrderModifier`·`OrderReader`(provided 인터페이스)와 `OrderInfo` 필드 구성은 그대로 유지해 `app-api`·`app-admin` 호출부를 변경하지 않는다.

## 비목표

- 5축 전부를 별도 애그리거트로 쪼개지 않는다(위 결합도 분석 근거).
- 낙관락을 비관락으로 전환하지 않는다.
- 새 비즈니스 규칙을 추가하지 않는다 — 행동은 리팩터 전후로 동일하다(신규 이벤트 소비자 1건 제외, 아래 참조).

## 최종 구조

### `Order` (기존 애그리거트, 축소)

테이블 `ordering.orders`는 유지하되 이행축 6개 컬럼(`fulfillment_status`·`shipped_at`·`carrier`·`tracking_number`·`delivered_at`·`hold_reason`)을 제거한다(contract 단계).

남는 상태:

| 축 | 표현 | 비고 |
|---|---|---|
| 결제(core) | 플랫 필드 유지 — `status`·`paidAt`·`stockDeductedAt`·`totalAmount`·`discountAmount`·`shippingFee`·`payAmount`·`issuedCouponId` | 애그리거트 정체성 자체라 추출 대상 아님 |
| 부분취소 | 플랫 필드 유지 — `refundedAmount` + `OrderLine` 자식 컬렉션 | 금액 불변식·라인 공유로 추출 불가 |
| 취소(전체) | 신규 `@Embeddable record Cancellation(cancelRequestedAt, cancelledAt, cancellationReason)` | 판별 유니온 패턴(entity-persistence.md 값 객체 매핑) |
| 반품 | 신규 `@Embeddable record ReturnRequest(returnStatus, returnRequestedAt, returnReason)` | `Return`은 자바 예약어라 `ReturnRequest`로 명명. 동일 패턴 |

- `Cancellation`·`ReturnRequest`는 불변 레코드이며 자기 축 전이 메서드를 갖는다(예: `Cancellation.request(now)`가 새 인스턴스를 반환). `Order`는 전이 후 `this.cancellation = this.cancellation.request(now)` 형태로 필드를 교체한다.
- 구현 중 발견: 취소·반품 이력이 없는 주문(임베더블 전 컴포넌트가 `null`)을 조회하면 Hibernate가 `cancellation`/`returnRequest` 필드 자체를 `null`로 접는다(레코드 임베더블도 예외 없음, `hibernate.create_empty_composites.enabled` 글로벌 설정도 이 경로에는 효과 없었다). `Order`는 이 필드를 직접 읽지 않고 방어적 접근자(`cancellation()`/`returnRequest()` — 필드가 `null`이면 빈 인스턴스로 대체)로만 읽는다.
- 컬럼 매핑은 기존과 동일(같은 테이블, 같은 컬럼명) — 임베더블 도입 자체는 스키마 변경이 아니다.
- `Order`의 7개 메서드(`cancel`·`requestCancellation`·`beginLineCancellation`·`refund`·`requestReturn`·`requestLineReturn`·`beginLineReturn`)는 현재 자기 필드로 읽던 `fulfillmentStatus`를 더 이상 갖지 않으므로 파라미터로 받는다. 예: `cancel(CancellationReason reason, FulfillmentStatus fulfillmentStatus, Instant now)`. 내부 헬퍼 `isShippedOrDelivered()`도 필드 대신 파라미터를 검사하도록만 바뀐다 — 판정 로직 자체는 그대로.
- `OrderModifier`(provided) 시그니처는 `UUID orderId` 등 기존 그대로다. `DefaultOrderModifier`가 `FulfillmentRepository`를 추가로 주입받아 필요한 메서드에서 `Fulfillment`를 먼저 읽고 그 상태를 `Order` 엔티티 메서드에 넘겨준다.

### `Fulfillment` (신규 애그리거트)

- 테이블 `ordering.fulfillment`, 컬럼: `id`(UUIDv7, PK) · `order_id`(UUID, 유니크 인덱스, ID 논리참조) · `status`(`FulfillmentStatus`) · `shipped_at` · `carrier` · `tracking_number` · `delivered_at` · `hold_reason` · `version` · `created_at` · `updated_at`.
- 엔티티 `Fulfillment extends BaseTimeEntity<UUID>`, 자체 `@Version`.
- 정적 팩토리 `create(UUID orderId)` — `PREPARING` 상태로 생성(아래 생성 조율 참조). `NOT_STARTED` enum 상수는 영속 상태로 갖지 않는다 — `PENDING`(결제 전) 주문은 애초에 `Fulfillment` 행이 없고, 조회 시점에 "행 없음"을 `NOT_STARTED`로 합성해 반환한다("엣지 케이스" 절 참조).
- 의도 동사 메서드는 기존 `Order`의 이행 메서드를 그대로 옮기되, 교차축(결제·취소) 사실을 파라미터로 받는다.
  - `ship(String carrier, String trackingNumber, boolean orderCancelInProgress, Instant now)` — 자기 축 가드(`PREPARING`인지)는 그대로 엔티티가 검사, `orderCancelInProgress`만 추가로 받아 `CANCEL_IN_PROGRESS` 판정.
  - `confirmDelivery(Instant now)`
  - `holdFulfillment(HoldReason reason)`
  - `releaseFulfillment()`
  - 위 4개 모두 "주문이 결제완료 상태인지"는 `DefaultOrderModifier`가 `Order`를 먼저 읽어 사전 검증하고(`NOT_PAID`), `Fulfillment` 엔티티 메서드에는 넘기지 않는다 — 결제완료 여부는 `Fulfillment` 자신의 존재 자체가 이미 전제(생성 조율 참조)하는 사실이라 축 전이 파라미터로 보지 않는다. `orderCancelInProgress`만 `ship()`에 전달하는 이유는 취소 축이 별도로 언제든 변할 수 있는 독립 사실이기 때문이다.
- `FulfillmentStatusException`·`OrderErrorCode`(`NOT_PAID`·`INVALID_FULFILLMENT_TRANSITION`·`CANCEL_IN_PROGRESS`)는 기존 파일 그대로 재사용한다. 파일 이동 없음 — 예외 정의 위치(`domain-order/domain/exception`)는 동일 모듈이라 바뀌지 않는다.

## 애플리케이션 계층 변경

### 생성 조율 — `OrderPaidListener`

- `Order.markPaid()`는 지금처럼 `Order`만 쓰고 `OrderPaid`를 발행한다(변경 없음).
- 신규 package-private 이벤트 소비자 `OrderPaidListener`(위치: `domain-order/application`)가 `OrderPaid`를 받아 `Fulfillment.create(orderId)`를 자기 트랜잭션에서 실행한다. 기존 `{이벤트명}Listener` 네이밍(`OrderPaidListener`가 `domain-cart`·`domain-wishlist`에 이미 존재)을 그대로 따른다 — 이번 건은 발행 도메인(order)이 자기 이벤트를 스스로 소비하는 경우라 다른 모듈이므로 이름이 겹치지 않는다.
- `order_id` 유니크 인덱스로 재전달 시 중복 생성을 막는다(멱등 가드).
- 결제·이행 생성을 한 트랜잭션에 몰지 않음으로써 "트랜잭션당 애그리거트 1개 변경" 규칙을 지킨다.

### 조회 — 크로스 애그리거트 쿼리

- `OrderRepository.findIdPageByStatusAndFulfillmentStatus`·`existsByMemberIdAndStatusAndFulfillmentStatusNot`(현재 파생 쿼리)는 더 이상 단일 테이블로 표현할 수 없다. `existsDeliveredLineByMemberIdAndProductId`가 이미 `join o.lines`로 자식 엔티티를 JPQL `@Query`로 조인하는 기존 패턴을 따라, `Fulfillment`를 별도 FROM 루트로 둔 JPQL 멀티 루트 쿼리(`from Order o, Fulfillment f where o.id = f.orderId and ...`)로 전환한다. `Order`·`Fulfillment` 사이 객체 연관(`@ManyToOne`/`@OneToMany`)은 두지 않으므로(애그리거트 간 참조는 ID만) 이 방식이 필요하다. `domain-order`에는 QueryDSL 프래그먼트·`adapter` 구역 선례가 아직 없어 새 인프라를 들이지 않고 기존 `@Query` 패턴을 그대로 확장한다.
- `DefaultOrderReader.getOrder(...)`·`getOrdersByMember(...)`·`findPendingBefore(...)`는 `Order`+`Fulfillment`를 함께 읽어 `OrderInfo`를 조립해야 한다. 단건은 `fulfillmentRepository.findByOrderId`, 목록/페이지는 ID 목록으로 벌크 조회(`findByOrderIdIn`)해 반복 단건 조회를 피한다.
- `OrderInfo.from(Order)`는 단일 원본 변환 규칙(coding-conventions.md)에 어긋나므로 `OrderInfo.of(Order, Fulfillment)`로 이름을 바꾼다.

### `module-query/query-order` 영향

- `DefaultOrderSearchReader`(회원 이메일 검색)가 `QOrder.fulfillmentStatus`를 직접 프로젝션한다. `QFulfillment` 조인을 추가해 같은 프로젝션 결과를 유지한다. `OrderSearchInfo` 레코드 형태는 변경 없음.

## 마이그레이션 (expand-contract)

1. **expand**: `ordering.fulfillment` 테이블 생성(인덱스: `order_id` 유니크) + `orders`의 기존 6개 컬럼값으로 백필 INSERT. `orders.fulfillment_status`는 원래 `NOT NULL`인데 `Order` 엔티티가 더는 이 컬럼을 채우지 않으므로, 같은 expand 마이그레이션에서 `ALTER TABLE ... ALTER COLUMN fulfillment_status DROP NOT NULL`로 제약을 먼저 완화해야 신규 INSERT가 막히지 않는다(구현 중 실제로 이 제약 위반이 재현돼 추가함).
2. 코드 전환: 위 도메인·애플리케이션·query 변경을 배포해 이행축 읽기·쓰기가 전부 `fulfillment` 테이블을 거치게 한다.
3. **contract**(`-- contract` 헤더 파일 분리): `orders`에서 `fulfillment_status`·`shipped_at`·`carrier`·`tracking_number`·`delivered_at`·`hold_reason` 6개 컬럼 제거.

## 엣지 케이스

- **조회(정상 경로)**: `Fulfillment` 행이 없는 경우는 `PENDING`(결제 전) 주문의 정상 상태다 — 결제 전이라 이행이 시작될 이유가 없다. `getOrder`류는 행 없음을 `FulfillmentStatus.NOT_STARTED`·이행 관련 필드 전부 `null`로 합성해 반환한다.
- **쓰기(진짜 레이스 창)**: `Order.markPaid()` 커밋과 `OrderPaidListener`의 `Fulfillment.create()` 커밋 사이 짧은 창에서 `ship`·`confirmDelivery`·`holdFulfillment`·`releaseFulfillment` 호출이 들어오면 `Order.status`는 이미 `PAID`인데 `Fulfillment` 행이 아직 없다. 신규 에러코드 `OrderErrorCode.FULFILLMENT_NOT_READY`(409)를 추가해 `FulfillmentStatusException`으로 던진다 — 클라이언트 재시도.

## 테스트 방침

- 이번 작업은 리팩터다(`testing.md`: "리팩터는 새 시나리오를 만들지 않는다. 전후 테스트 통과가 기준이다") — `Order`·`Fulfillment`로 갈라지는 기존 행동은 새 시나리오 합의 없이 전후 테스트 통과로 검증한다.
- `OrderPaidListener`(신규 생성 조율)는 새 행동이므로 시나리오 문장을 별도 합의한다 — 최소 "결제완료 시 Fulfillment가 PREPARING으로 생성됨"·"이벤트 중복 소비 시 1회만 생성됨(멱등)".
- `OrderTest.java`(801줄)는 결제·취소·부분취소·반품 축만 남아 자연히 축소된다. 이행축 테스트는 신규 `FulfillmentTest`로 옮긴다 — 각 전이 메서드를 `Order` 없이 파라미터(불리언)로 직접 검증할 수 있어 픽스처가 단순해진다.
- `OrderPersistenceTest`에 대응하는 `FulfillmentPersistenceTest` 신설, `DefaultOrderModifier`/`DefaultOrderReader` 테스트는 두 리포지토리 조율을 반영해 갱신한다.
- `query-order`의 `DefaultOrderSearchReader` 테스트는 조인 추가를 반영해 갱신한다(행동·응답 형태는 불변).

## 리스크·트레이드오프

- **엔티티 자기완결성 약화**: `Order`의 취소·반품 계열 7개 메서드, `Fulfillment`의 `ship()`이 더 이상 필요한 사실을 스스로 조회하지 않고 파라미터로 받는다. 교차 애그리거트 불변식 검사가 엔티티에서 서비스 계층(`DefaultOrderModifier`)으로 일부 이동한다 — 표준적인 DDD 트레이드오프이지만 리뷰 시 파라미터 전달 누락(예: `cancelInProgress` 계산 실수)을 코드 검토로 잡아야 한다.
- **구현 범위**: 신규 엔티티·테이블·리포지토리·이벤트 소비자, 7+4개 메서드 시그니처 변경, QueryDSL 프래그먼트 2건 신설, `query-order` 조인 추가, expand-contract 마이그레이션 2건. 파일 수 기준 체감 규모가 크다.
- **거짓 낙관락 충돌 해소는 이번 목표에 포함**: 사고는 없었지만 A(값 객체 추출)만으로는 해결 안 되던 문제(이행 vs 취소/반품 동시 쓰기 충돌)를 B(이행축 분리)가 근본 해결한다 — 리스크 대비 실질 이득이 있는 부분.

## 문서 동반 갱신

- `DOMAIN_MODEL.md` "6. 주문 (order)" 절: `Order` 필드 목록에서 이행축 6개 제거, 신규 "Fulfillment" 애그리거트 하위 절 추가, "정본 애그리거트 예시" 서술을 분해 사례로 갱신.
- `REQUIREMENTS.md`는 갱신 대상이 아니다 — 이행 관련 서술(186~188행)은 행동 기술이라 애그리거트 구조 변경과 무관하게 그대로 성립한다(확인 완료).

## 완료 기준

- 기존 `Order`·`OrderModifier`·`OrderReader`·`OrderInfo` 관련 시나리오가 전후 동일하게 통과한다(`./gradlew build` 그린).
- `OrderPaidListener`의 합의된 신규 시나리오(생성·멱등)에 대응하는 `@DisplayName` 테스트가 있고 통과한다.
- `app-api`·`app-admin`의 `OrderModifier`·`OrderReader` 호출부 코드가 변경되지 않는다(계약 안정성 확인).
- 아키텍처 테스트(리포지토리는 애그리거트 루트당 1개, 트랜잭션당 애그리거트 1개 등)가 새 `Fulfillment` 애그리거트에도 그대로 통과한다.
- expand 마이그레이션 적용 후 코드 전환 완료 시점에만 contract 마이그레이션(`-- contract` 헤더)을 별도 커밋으로 추가한다.
- `DOMAIN_MODEL.md` 서술이 최종 구조와 일치한다.

# Order 애그리거트 분해 (이행축 분리) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Order.java`(731줄)에서 이행축(배송)을 별도 `Fulfillment` 애그리거트로 분리하고, 남는 취소·반품 축을 `@Embeddable record` 값 객체로 재편해 애그리거트 크기와 교차축 결합을 줄인다.

**Architecture:** `Order`는 결제 core + 부분취소(금액·`OrderLine`)만 남기고 취소·반품은 `Cancellation`·`ReturnRequest` 임베더블로 내부 정리한다. 이행축은 신규 `Fulfillment` 애그리거트(별도 테이블·리포지토리·`@Version`)로 완전히 옮기고, 생성은 기존 `OrderPaid` 이벤트를 도메인이 자기소비하는 신규 `OrderPaidListener`가 조율한다. `OrderModifier`·`OrderReader`·`OrderInfo`(provided 계약)는 시그니처를 그대로 유지해 `app-api`·`app-admin` 호출부는 변경하지 않는다.

**Tech Stack:** Spring Boot, Spring Data JPA(JPQL `@Query`), Flyway(PostgreSQL), JUnit 5 + AssertJ + Testcontainers(`@DataJpaTest`).

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-07-24-order-fulfillment-split-design.md` (이 계획의 근거, 트레이드오프·비목표 포함).
- 물리 FK 금지 — `Order`↔`Fulfillment` 참조는 `orderId`(UUID 논리참조)만 사용한다.
- 상태 enum은 `@Enumerated(EnumType.STRING)`만 사용한다.
- 신규 ID는 `UuidV7Generator.generate()`로 애플리케이션이 생성한다(`@GeneratedValue` 금지).
- 낙관락(`@Version`)은 last-write-wins 기본, 서버는 자동 재시도하지 않는다.
- 리팩터 범위(`Order`·`OrderModifier`·`OrderReader`·`OrderInfo`·query-order)는 새 시나리오를 만들지 않는다 — 전후 테스트 통과가 기준이다(`testing.md`).
- `OrderPaidListener`(신규 생성 조율 행동)만 TDD 대상이다 — 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- 스키마 파괴 변경(컬럼 제거)은 expand-contract 2단계로 분리한다. contract 파일은 첫 줄에 `-- contract` 주석을 둔다.
- 마이그레이션 파일에 `RENAME`·크로스 스키마 참조·FK 제약을 쓰지 않는다.
- 모든 신규 파일은 Spotless 포맷·NullAway·Error Prone 게이트를 통과해야 한다(`./gradlew build`가 최종 검증).

---

## Task 1: Fulfillment 애그리거트 신설 (도메인 단위)

새 애그리거트 루트 `Fulfillment`를 만든다. 이 시점에는 `Order`를 건드리지 않으므로 기존 코드는 그대로 컴파일·통과한다.

**Files:**
- Create: `module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Fulfillment.java`
- Test: `module-domains/domain-order/src/test/java/com/commerce/domain/order/domain/FulfillmentTest.java`

**Interfaces:**
- Produces: `Fulfillment.create(UUID orderId): Fulfillment` (PREPARING 생성), `ship(String carrier, String trackingNumber, boolean orderCancelInProgress, Instant now)`, `confirmDelivery(Instant now)`, `hold(HoldReason reason)`, `release()`, `getId()`, `getOrderId()`, `getStatus()`, `getShippedAt()`, `getCarrier()`, `getTrackingNumber()`, `getDeliveredAt()`, `getHoldReason()`, `getVersion()`. 이후 Task 3(`DefaultOrderModifier`)이 이 시그니처를 그대로 소비한다.

- [ ] **Step 1: FulfillmentTest.java 작성**

```java
package com.commerce.domain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FulfillmentTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");
    private static final String CARRIER = "CJ대한통운";
    private static final String TRACKING_NUMBER = "688900123456";
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("생성 시 PREPARING이고 준비→출고→배송 완료로 이행한다")
    void fulfillmentFlow() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(fulfillment.getOrderId()).isEqualTo(ORDER_ID);

        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);

        fulfillment.confirmDelivery(NOW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("출고 시 택배사·운송장 번호를 기록하고 출고 전에는 null이다")
    void shipRecordsTrackingInfo() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        assertThat(fulfillment.getCarrier()).isNull();
        assertThat(fulfillment.getTrackingNumber()).isNull();

        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);

        assertThat(fulfillment.getCarrier()).isEqualTo(CARRIER);
        assertThat(fulfillment.getTrackingNumber()).isEqualTo(TRACKING_NUMBER);
    }

    @Test
    @DisplayName("준비 중이 아니면 출고할 수 없다")
    void shipRequiresPreparing() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION));
    }

    @Test
    @DisplayName("주문의 취소가 진행 중이면 출고를 거부한다")
    void shipRejectedWhileCancellationInProgress() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, true, NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
    }

    @Test
    @DisplayName("출고된 상태가 아니면 배송 완료 처리할 수 없다")
    void confirmDeliveryRequiresShipped() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);

        assertThatThrownBy(() -> fulfillment.confirmDelivery(NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION));
    }

    @Test
    @DisplayName("보류와 해제를 오간다. 보류 중에는 출고할 수 없다")
    void holdAndRelease() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        fulfillment.hold(HoldReason.FRAUD_REVIEW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.ON_HOLD);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW))
                .isInstanceOf(FulfillmentStatusException.class);

        fulfillment.release();
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(fulfillment.getHoldReason()).isNull();
    }

    @Test
    @DisplayName("준비 중이 아니면 보류할 수 없고, 보류 중이 아니면 해제할 수 없다")
    void holdAndReleaseRequireExpectedState() {
        Fulfillment shipped = Fulfillment.create(ORDER_ID);
        shipped.ship(CARRIER, TRACKING_NUMBER, false, NOW);
        assertThatThrownBy(() -> shipped.hold(HoldReason.STOCK_DELAY))
                .isInstanceOf(FulfillmentStatusException.class);

        Fulfillment preparing = Fulfillment.create(ORDER_ID);
        assertThatThrownBy(preparing::release).isInstanceOf(FulfillmentStatusException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행해 컴파일 실패 확인**

Run: `./gradlew :module-domains:domain-order:compileTestJava`
Expected: FAIL — `Fulfillment` cannot be resolved (아직 존재하지 않음)

- [ ] **Step 3: Fulfillment.java 구현**

```java
package com.commerce.domain.order.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 이행(fulfillment) 애그리거트 루트다. 결제 완료된 주문마다 하나씩, 결제 완료 시점에 준비 중으로 생성된다. */
@Entity
@Table(schema = "ordering", name = "fulfillment")
public class Fulfillment extends BaseTimeEntity<UUID> {

    /** 이행 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 이행 대상 주문 식별자. order 애그리거트 논리 참조. */
    @Column(name = "order_id")
    private UUID orderId;

    /** 이행 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FulfillmentStatus status;

    /** 출고 시각. */
    @Column(name = "shipped_at")
    @Nullable
    private Instant shippedAt;

    /** 택배사. */
    @Column(name = "carrier")
    @Nullable
    private String carrier;

    /** 운송장 번호. */
    @Column(name = "tracking_number")
    @Nullable
    private String trackingNumber;

    /** 배송 완료 시각. */
    @Column(name = "delivered_at")
    @Nullable
    private Instant deliveredAt;

    /** 이행 보류 사유. 보류 중일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason")
    @Nullable
    private HoldReason holdReason;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    protected Fulfillment() {}

    private Fulfillment(UUID id, UUID orderId) {
        this.id = id;
        this.orderId = orderId;
        this.status = FulfillmentStatus.PREPARING;
    }

    /** 결제 완료된 주문의 이행을 준비 중으로 생성한다. */
    public static Fulfillment create(UUID orderId) {
        return new Fulfillment(UuidV7Generator.generate(), orderId);
    }

    /**
     * 출고한다. 택배사·운송장 번호를 기록한다.
     *
     * @throws FulfillmentStatusException 준비 중이 아니거나 주문의 취소가 진행 중이면
     */
    public void ship(String carrier, String trackingNumber, boolean orderCancelInProgress, Instant now) {
        if (status != FulfillmentStatus.PREPARING) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        if (orderCancelInProgress) {
            throw new FulfillmentStatusException(OrderErrorCode.CANCEL_IN_PROGRESS);
        }
        this.status = FulfillmentStatus.SHIPPED;
        this.shippedAt = now;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /**
     * 배송 완료 처리한다.
     *
     * @throws FulfillmentStatusException 출고된 상태가 아니면
     */
    public void confirmDelivery(Instant now) {
        if (status != FulfillmentStatus.SHIPPED) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.DELIVERED;
        this.deliveredAt = now;
    }

    /**
     * 이행을 보류한다.
     *
     * @throws FulfillmentStatusException 준비 중이 아니면
     */
    public void hold(HoldReason reason) {
        if (status != FulfillmentStatus.PREPARING) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.ON_HOLD;
        this.holdReason = reason;
    }

    /**
     * 이행 보류를 해제한다.
     *
     * @throws FulfillmentStatusException 보류 중이 아니면
     */
    public void release() {
        if (status != FulfillmentStatus.ON_HOLD) {
            throw new FulfillmentStatusException(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION);
        }
        this.status = FulfillmentStatus.PREPARING;
        this.holdReason = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }

    public @Nullable Instant getShippedAt() {
        return shippedAt;
    }

    public @Nullable String getCarrier() {
        return carrier;
    }

    public @Nullable String getTrackingNumber() {
        return trackingNumber;
    }

    public @Nullable Instant getDeliveredAt() {
        return deliveredAt;
    }

    public @Nullable HoldReason getHoldReason() {
        return holdReason;
    }

    public long getVersion() {
        return version;
    }
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew :module-domains:domain-order:test --tests "com.commerce.domain.order.domain.FulfillmentTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Fulfillment.java module-domains/domain-order/src/test/java/com/commerce/domain/order/domain/FulfillmentTest.java
git commit -m "feat: Fulfillment 애그리거트 신설 — 이행축 독립 엔티티"
```

---

## Task 2: 이행 테이블 확장 마이그레이션 (expand)

`ordering.fulfillment` 테이블을 만들고 기존 `orders`의 이행축 데이터를 백필한다. 기존 `orders` 컬럼은 아직 남겨둔다(코드 전환은 Task 3). 순수 추가 DDL이라 기존 테스트는 전부 그대로 통과해야 한다.

**Files:**
- Create: `module-domains/domain-order/src/main/resources/db/migration/ordering/V4__create_fulfillment_table.sql`

**Interfaces:**
- Produces: 테이블 `ordering.fulfillment(id, order_id, status, shipped_at, carrier, tracking_number, delivered_at, hold_reason, version, created_at, updated_at)`, 유니크 인덱스 `ux_fulfillment_order_id`. Task 3의 `Fulfillment` `@Table`·`FulfillmentRepository`가 이 스키마를 전제한다.

- [ ] **Step 1: 마이그레이션 파일 작성**

```sql
CREATE TABLE ordering.fulfillment (
    id              UUID        NOT NULL,
    order_id        UUID        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    shipped_at      TIMESTAMPTZ,
    carrier         VARCHAR(100),
    tracking_number VARCHAR(100),
    delivered_at    TIMESTAMPTZ,
    hold_reason     VARCHAR(30),
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_fulfillment PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_fulfillment_order_id ON ordering.fulfillment (order_id);

-- 이행이 시작된(NOT_STARTED가 아닌) 기존 주문만 백필한다. PENDING 주문은 이행 행이 없는 것이 정상 상태다.
INSERT INTO ordering.fulfillment
    (id, order_id, status, shipped_at, carrier, tracking_number, delivered_at, hold_reason, version, created_at, updated_at)
SELECT
    gen_random_uuid(), id, fulfillment_status, shipped_at, carrier, tracking_number, delivered_at, hold_reason, 0, created_at, updated_at
FROM ordering.orders
WHERE fulfillment_status <> 'NOT_STARTED';
```

- [ ] **Step 2: 기존 테스트 스위트로 회귀 확인**

Run: `./gradlew :module-domains:domain-order:test`
Expected: PASS — 새 테이블은 아직 어떤 코드도 참조하지 않으므로 기존 시나리오(`OrderTest`·`OrderPersistenceTest`)가 그대로 통과한다.

- [ ] **Step 3: Commit**

```bash
git add module-domains/domain-order/src/main/resources/db/migration/ordering/V4__create_fulfillment_table.sql
git commit -m "feat: 이행 테이블 확장 마이그레이션 — 기존 이행 데이터 백필"
```

---

## Task 3: Order 축소·이행축 애플리케이션 계층 컷오버

가장 큰 작업이다. `Order`에서 이행축을 제거하고 취소·반품축을 임베더블로 정리하며, `FulfillmentRepository`·`OrderRepository`·`DefaultOrderModifier`·`DefaultOrderReader`·`OrderInfo`·`OrderPaidListener`를 함께 바꾼다. 이 파일들은 서로 컴파일 의존이 얽혀 있어(예: `Order`의 메서드 시그니처를 `DefaultOrderModifier`가 바로 호출) 개별 파일 단위로는 중간에 빌드가 성립하지 않는다 — 전 파일을 작성한 뒤 한 번에 빌드·테스트한다.

**Files:**
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/exception/OrderErrorCode.java`
- Create: `module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Cancellation.java`
- Create: `module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/ReturnRequest.java`
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Order.java` (전면 재작성)
- Modify: `module-domains/domain-order/src/test/java/com/commerce/domain/order/domain/OrderTest.java` (전면 재작성)
- Create: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/required/FulfillmentRepository.java`
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/required/OrderRepository.java`
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/DefaultOrderModifier.java`
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/info/OrderInfo.java`
- Modify: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/DefaultOrderReader.java`
- Create: `module-domains/domain-order/src/main/java/com/commerce/domain/order/application/OrderPaidListener.java`
- Test: `module-domains/domain-order/src/test/java/com/commerce/domain/order/application/OrderPaidListenerTest.java`
- Modify: `module-domains/domain-order/src/test/java/com/commerce/domain/order/application/OrderPersistenceTest.java` (이행 시나리오 제거)
- Test: `module-domains/domain-order/src/test/java/com/commerce/domain/order/application/FulfillmentPersistenceTest.java`

**Interfaces:**
- Consumes: Task 1의 `Fulfillment`(`create`·`ship`·`confirmDelivery`·`hold`·`release`·게터), Task 2의 `ordering.fulfillment` 테이블.
- Produces: `OrderModifier`·`OrderReader`(provided, `application/provided/`에 위치 — 시그니처 무변경, 이 태스크에서 파일 수정 없음), `OrderInfo.of(Order, @Nullable Fulfillment): OrderInfo`, `FulfillmentRepository.findByOrderId(UUID): Optional<Fulfillment>`·`findByOrderIdIn(Collection<UUID>): List<Fulfillment>`. Task 4(query-order)는 이 타입들을 직접 쓰지 않지만 같은 모듈 컨벤션을 따른다.

### Step A — OrderErrorCode에 FULFILLMENT_NOT_READY 추가

- [ ] **Step 1: OrderErrorCode.java 수정**

`module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/exception/OrderErrorCode.java`의 `NOT_PAID` 상수 다음에 추가:

```java
    NOT_PAID("ORDER_NOT_PAID", "결제 완료 주문만 이행할 수 있다.", 409),
    FULFILLMENT_NOT_READY("ORDER_FULFILLMENT_NOT_READY", "이행 생성이 아직 반영되지 않았다. 잠시 후 다시 시도해 달라.", 409),
```
(`NOT_PAID` 줄 뒤에 `FULFILLMENT_NOT_READY` 줄을 삽입한다. 다른 상수는 그대로 둔다.)

### Step B — Cancellation·ReturnRequest 값 객체

- [ ] **Step 2: Cancellation.java 작성**

```java
package com.commerce.domain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 주문 취소 축 값 객체다. 전 컴포넌트가 없으면(null) 취소 이력이 없는 주문이다.
 *
 * @param cancelRequestedAt 취소 개시 마커. 개시되지 않았으면 없다
 * @param cancelledAt 취소 시각. 취소되지 않았으면 없다
 * @param cancellationReason 취소 사유. 취소되지 않았으면 없다
 */
@Embeddable
record Cancellation(
        @Column(name = "cancel_requested_at") @Nullable Instant cancelRequestedAt,
        @Column(name = "cancelled_at") @Nullable Instant cancelledAt,
        @Enumerated(EnumType.STRING) @Column(name = "cancellation_reason") @Nullable
                CancellationReason cancellationReason) {

    /** 취소 개시 마커를 남긴다. 이미 개시됐으면 그대로 반환한다(재개시 no-op). */
    Cancellation request(Instant now) {
        return cancelRequestedAt != null ? this : new Cancellation(now, null, null);
    }

    /** 취소를 완결해 시각·사유를 기록한다. 개시 마커는 있던 그대로 보존한다. */
    Cancellation complete(CancellationReason reason, Instant now) {
        return new Cancellation(cancelRequestedAt, now, reason);
    }
}
```

- [ ] **Step 3: ReturnRequest.java 작성**

```java
package com.commerce.domain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 주문 단위 반품 요청 축 값 객체다. 전 컴포넌트가 없으면(null) 반품 요청 이력이 없는 주문이다.
 *
 * @param returnStatus 반품 요청 축 상태. 요청이 없으면 없다
 * @param returnRequestedAt 반품 요청 시각. 요청이 없으면 없다
 * @param returnReason 반품 요청 사유. 요청이 없으면 없다
 */
@Embeddable
record ReturnRequest(
        @Enumerated(EnumType.STRING) @Column(name = "return_status") @Nullable ReturnStatus returnStatus,
        @Column(name = "return_requested_at") @Nullable Instant returnRequestedAt,
        @Enumerated(EnumType.STRING) @Column(name = "return_reason") @Nullable RefundReason returnReason) {

    /** 반품을 요청 상태로 남긴다. */
    ReturnRequest request(RefundReason reason, Instant now) {
        return new ReturnRequest(ReturnStatus.REQUESTED, now, reason);
    }

    /** 반품 요청을 거절한다. 요청 시각·사유는 그대로 보존한다. */
    ReturnRequest reject() {
        return new ReturnRequest(ReturnStatus.REJECTED, returnRequestedAt, returnReason);
    }

    /** 반품 요청을 완료로 종결한다. 요청 시각·사유는 그대로 보존한다. */
    ReturnRequest complete() {
        return new ReturnRequest(ReturnStatus.COMPLETED, returnRequestedAt, returnReason);
    }
}
```

### Step C — Order.java 전면 재작성

- [ ] **Step 4: Order.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import com.commerce.domain.order.domain.exception.InvalidOrderException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderLineNotFoundException;
import com.commerce.domain.order.domain.exception.OrderStatusException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.shared.entity.MoneyConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** 주문 애그리거트 루트다. 라인·배송지를 주문 시점 스냅샷으로 보관한다. 이행(배송) 축은 별도 {@link Fulfillment} 애그리거트가 갖는다. */
@Entity
@Table(schema = "ordering", name = "orders")
public class Order extends BaseTimeEntity<UUID> {

    /** 주문 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 외부 노출용 주문 번호. */
    @Column(name = "order_number")
    private String orderNumber;

    /** 주문한 회원 식별자. member 도메인 논리 참조. */
    @Column(name = "member_id")
    private UUID memberId;

    /** 결제 축 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    /** 라인 합계. 할인·배송비 반영 전. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amount")
    private Money totalAmount;

    /** 쿠폰 할인액. 라인 합계를 넘지 못한다. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "discount_amount")
    private Money discountAmount;

    /** 배송비. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "shipping_fee")
    private Money shippingFee;

    /** 실청구액. 라인 합계 − 할인액 + 배송비. */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "pay_amount")
    private Money payAmount;

    /** 부분 취소로 환불된 누계. 라인 취소마다 누적되어 루트 버전을 증가시킨다(동시 취소 직렬화). */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "refunded_amount")
    private Money refundedAmount;

    /** 적용한 쿠폰 발급분 식별자. 할인액이 0이면 없다. */
    @Column(name = "issued_coupon_id")
    @Nullable
    private UUID issuedCouponId;

    /** 배송지. 주문 시점 스냅샷. */
    @Embedded
    private Address shippingAddress;

    /** 전 라인 재고 차감 완료 시각. */
    @Column(name = "stock_deducted_at")
    @Nullable
    private Instant stockDeductedAt;

    /** 결제 완료 시각. */
    @Column(name = "paid_at")
    @Nullable
    private Instant paidAt;

    /** 취소 축. */
    @Embedded
    private Cancellation cancellation;

    /** 환불 시각. */
    @Column(name = "refunded_at")
    @Nullable
    private Instant refundedAt;

    /** 환불 사유. */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_reason")
    @Nullable
    private RefundReason refundReason;

    /** 주문 단위 반품 요청 축. */
    @Embedded
    private ReturnRequest returnRequest;

    /** 낙관락 버전. */
    @Version
    @Column(name = "version")
    private long version;

    /** 주문 라인 집합. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderLine> lines = new HashSet<>();

    protected Order() {}

    private Order(UUID id, String orderNumber, UUID memberId, Address shippingAddress, Money shippingFee) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
        this.shippingFee = shippingFee;
        this.status = OrderStatus.PENDING;
        this.refundedAmount = Money.ZERO;
        this.cancellation = new Cancellation(null, null, null);
        this.returnRequest = new ReturnRequest(null, null, null);
    }

    /**
     * 주문을 생성한다. 라인 합계를 자기 계산하고 할인·실청구액 불변식을 자기 강제한다.
     *
     * @throws InvalidOrderException 라인이 없거나, 할인이 주문 금액을 초과하거나, 쿠폰과 할인액이 불일치하면
     */
    public static Order place(
            UUID memberId,
            List<OrderLineSnapshot> lineSnapshots,
            Address shippingAddress,
            Money discountAmount,
            Money shippingFee,
            @Nullable UUID issuedCouponId) {
        if (lineSnapshots.isEmpty()) {
            throw new InvalidOrderException(OrderErrorCode.EMPTY_ORDER);
        }
        UUID orderId = UuidV7Generator.generate();
        Order order = new Order(orderId, generateOrderNumber(orderId), memberId, shippingAddress, shippingFee);
        for (OrderLineSnapshot snapshot : lineSnapshots) {
            order.lines.add(OrderLine.create(order, snapshot));
        }
        order.totalAmount = order.computeTotal();
        order.applyDiscount(discountAmount, issuedCouponId);
        return order;
    }

    /**
     * 결제를 완료한다.
     *
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    public void markPaid(Instant now) {
        if (status != OrderStatus.PENDING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.PAID;
        this.paidAt = now;
    }

    /**
     * 전 라인 재고 차감 완료를 기록한다.
     *
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    public void markStockDeducted(Instant now) {
        if (status != OrderStatus.PENDING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.stockDeductedAt = now;
    }

    /**
     * 주문을 취소한다.
     *
     * @throws OrderStatusException 이미 취소·환불됐거나 출고 이후면
     */
    public void cancel(CancellationReason reason, FulfillmentStatus fulfillmentStatus, Instant now) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status == OrderStatus.PAID && isShippedOrDelivered(fulfillmentStatus)) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellation = this.cancellation.complete(reason, now);
    }

    /**
     * 취소 개시를 기록한다. 마커가 있는 동안 출고가 거부된다. 이미 개시된 주문에는 아무것도 하지 않는다.
     *
     * @throws OrderStatusException 결제 완료 주문이 아니거나 출고 이후면
     */
    public void requestCancellation(FulfillmentStatus fulfillmentStatus, Instant now) {
        if (cancellation.cancelRequestedAt() != null) {
            return;
        }
        // 부분 환불 이력이 있으면 전액 PG 취소가 이중 환불이 된다 — 남은 라인은 라인 단위로 취소한다.
        if (!refundedAmount.isZero()) {
            throw new OrderStatusException(OrderErrorCode.PARTIALLY_CANCELLED);
        }
        if (status != OrderStatus.PAID) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (isShippedOrDelivered(fulfillmentStatus)) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        this.cancellation = this.cancellation.request(now);
    }

    /**
     * 배송 완료된 주문을 전체 반품 환불 처리한다.
     *
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    public void refund(RefundReason reason, FulfillmentStatus fulfillmentStatus, Instant now) {
        if (status == OrderStatus.REFUNDED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        // 부분 환불 이력이 있으면 전액 PG 취소·전 라인 재고 복원이 이중 집행이 된다 — 잔여는 라인 단위로 처리한다.
        if (!refundedAmount.isZero()) {
            throw new OrderStatusException(OrderErrorCode.PARTIALLY_CANCELLED);
        }
        // 라인 이력(요청·진행·종결)이 하나라도 있으면 전액 집행이 이중 집행·요청 무시가 된다.
        // 환불 미확정(RETURN_REQUESTED)과 0원 환불 라인은 refundedAmount 프록시가 침묵하므로 상태로 막는다.
        if (lines.stream().anyMatch(line -> line.getStatus() != OrderLineStatus.ORDERED)) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.REFUND_NOT_ALLOWED);
        }
        this.status = OrderStatus.REFUNDED;
        this.refundedAt = now;
        this.refundReason = reason;
        if (returnRequest.returnStatus() == ReturnStatus.REQUESTED) {
            this.returnRequest = this.returnRequest.complete();
        }
    }

    /**
     * 반품을 요청한다. 거절된 요청은 새 요청으로 덮어쓴다.
     *
     * @throws OrderStatusException 이미 요청 중이거나, 배송 완료된 결제 주문이 아니면
     */
    public void requestReturn(RefundReason reason, FulfillmentStatus fulfillmentStatus, Instant now) {
        if (returnRequest.returnStatus() == ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_ALREADY_REQUESTED);
        }
        // 부분 환불 이력이 있으면 전체 반품 환불이 이중 집행이 된다 — 잔여 라인은 라인 단위로 반품한다.
        if (!refundedAmount.isZero()) {
            throw new OrderStatusException(OrderErrorCode.PARTIALLY_CANCELLED);
        }
        if (lines.stream()
                .anyMatch(line -> line.getStatus() == OrderLineStatus.RETURN_REQUESTED
                        || line.getStatus() == OrderLineStatus.RETURNING)) {
            throw new OrderStatusException(OrderErrorCode.RETURN_ALREADY_REQUESTED);
        }
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_ALLOWED);
        }
        this.returnRequest = this.returnRequest.request(reason, now);
    }

    /**
     * 반품 요청을 거절한다. 주문은 PAID·DELIVERED로 남는다.
     *
     * @throws OrderStatusException 반품 요청 상태가 아니면
     */
    public void rejectReturn() {
        if (returnRequest.returnStatus() != ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_REQUESTED);
        }
        this.returnRequest = this.returnRequest.reject();
    }

    /** 라인 금액을 합산해 라인 합계를 계산한다. */
    private Money computeTotal() {
        return lines.stream().map(OrderLine::lineAmount).reduce(Money.ZERO, Money::plus);
    }

    /** 할인·쿠폰 불변식을 검사하고 할인액·쿠폰·실청구액을 채운다. */
    private void applyDiscount(Money discountAmount, @Nullable UUID issuedCouponId) {
        if (totalAmount.isLessThan(discountAmount)) {
            throw new InvalidOrderException(OrderErrorCode.DISCOUNT_EXCEEDS_TOTAL);
        }
        boolean hasCoupon = issuedCouponId != null;
        boolean hasDiscount = !discountAmount.isZero();
        if (hasCoupon != hasDiscount) {
            throw new InvalidOrderException(OrderErrorCode.INVALID_DISCOUNT_COUPON);
        }
        this.discountAmount = discountAmount;
        this.issuedCouponId = issuedCouponId;
        this.payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
    }

    /** 이행 축 상태가 출고 이후인지 판정한다. */
    private static boolean isShippedOrDelivered(FulfillmentStatus fulfillmentStatus) {
        return fulfillmentStatus == FulfillmentStatus.SHIPPED || fulfillmentStatus == FulfillmentStatus.DELIVERED;
    }

    /** 주문 식별자에서 외부 노출용 주문 번호를 만든다. */
    private static String generateOrderNumber(UUID orderId) {
        // UUIDv7의 난수 꼬리(마지막 세그먼트)를 붙여 같은 밀리초 동시 생성에도 충돌하지 않게 한다.
        return "ORD-" + System.currentTimeMillis() + "-"
                + orderId.toString().substring(24).toUpperCase(Locale.ROOT);
    }

    /** 주문된 변형 집합을 변경 불가 뷰로 반환한다. */
    public Set<UUID> getOrderedVariantIds() {
        return lines.stream().map(OrderLine::getVariantId).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getShippingFee() {
        return shippingFee;
    }

    public Money getPayAmount() {
        return payAmount;
    }

    public @Nullable UUID getIssuedCouponId() {
        return issuedCouponId;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public @Nullable Instant getStockDeductedAt() {
        return stockDeductedAt;
    }

    public @Nullable Instant getPaidAt() {
        return paidAt;
    }

    public @Nullable Instant getCancelRequestedAt() {
        return cancellation.cancelRequestedAt();
    }

    public @Nullable Instant getCancelledAt() {
        return cancellation.cancelledAt();
    }

    public @Nullable CancellationReason getCancellationReason() {
        return cancellation.cancellationReason();
    }

    public @Nullable Instant getRefundedAt() {
        return refundedAt;
    }

    public @Nullable RefundReason getRefundReason() {
        return refundReason;
    }

    public @Nullable ReturnStatus getReturnStatus() {
        return returnRequest.returnStatus();
    }

    public @Nullable Instant getReturnRequestedAt() {
        return returnRequest.returnRequestedAt();
    }

    public @Nullable RefundReason getReturnReason() {
        return returnRequest.returnReason();
    }

    /** 주문 라인 집합을 변경 불가 뷰로 반환한다. */
    public Set<OrderLine> getLines() {
        return Collections.unmodifiableSet(lines);
    }

    public Money getRefundedAmount() {
        return refundedAmount;
    }

    /**
     * 라인 취소를 개시한다. 환불액을 확정해 라인에 기록하고 환불 누계에 더한 뒤 확정 환불액을 반환한다.
     *
     * <p>환불액은 쿠폰 할인의 라인 금액 비례 안분(내림)을 뺀 라인 금액이고, 마지막 활성 라인이면 결제
     * 잔액 전액이다(끝전·배송비 흡수).
     *
     * @throws OrderStatusException 결제 완료·출고 전 주문이 아니거나, 전체 취소가 진행 중이거나, 라인이 주문됨 상태가 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public Money beginLineCancellation(UUID lineId, FulfillmentStatus fulfillmentStatus) {
        if (status != OrderStatus.PAID || isShippedOrDelivered(fulfillmentStatus)) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_NOT_ALLOWED);
        }
        if (cancellation.cancelRequestedAt() != null) {
            throw new OrderStatusException(OrderErrorCode.CANCEL_IN_PROGRESS);
        }
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.ORDERED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        Money refund = computeLineRefund(line);
        line.markCancelling(refund);
        this.refundedAmount = this.refundedAmount.plus(refund);
        return refund;
    }

    /**
     * 취소 진행 중 라인을 취소로 완결한다. 전 라인이 취소되면 주문을 취소로 수렴시키고 참을 반환한다.
     *
     * @throws OrderStatusException 라인이 취소 진행 중이 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public boolean completeLineCancellation(UUID lineId, Instant now) {
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.CANCELLING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        line.completeCancellation();
        boolean allCancelled = lines.stream().allMatch(l -> l.getStatus() == OrderLineStatus.CANCELLED);
        if (allCancelled) {
            this.status = OrderStatus.CANCELLED;
            this.cancellation = this.cancellation.complete(CancellationReason.CUSTOMER_REQUEST, now);
        }
        return allCancelled;
    }

    /**
     * 라인 반품을 요청한다.
     *
     * @throws OrderStatusException 배송 완료된 결제 주문이 아니거나, 전체 반품이 진행 중이거나, 라인이 주문됨 상태가 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public void requestLineReturn(UUID lineId, RefundReason reason, FulfillmentStatus fulfillmentStatus) {
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_ALLOWED);
        }
        if (returnRequest.returnStatus() == ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_ALREADY_REQUESTED);
        }
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.ORDERED) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        line.requestReturn(reason);
    }

    /**
     * 라인 반품 요청을 거절한다. 라인은 주문됨으로 돌아가 재요청할 수 있다.
     *
     * @throws OrderStatusException 라인이 반품 요청 상태가 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public void rejectLineReturn(UUID lineId) {
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.RETURN_REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_REQUESTED);
        }
        line.rejectReturn();
    }

    /**
     * 라인 반품 승인을 개시한다. 환불액을 확정해 라인에 기록하고 환불 누계에 더한 뒤 확정 환불액을 반환한다.
     *
     * @throws OrderStatusException 라인이 반품 요청 상태가 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public Money beginLineReturn(UUID lineId, FulfillmentStatus fulfillmentStatus) {
        if (status != OrderStatus.PAID || fulfillmentStatus != FulfillmentStatus.DELIVERED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_ALLOWED);
        }
        if (returnRequest.returnStatus() == ReturnStatus.REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_ALREADY_REQUESTED);
        }
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.RETURN_REQUESTED) {
            throw new OrderStatusException(OrderErrorCode.RETURN_NOT_REQUESTED);
        }
        Money refund = computeLineRefund(line);
        line.markReturning(refund);
        this.refundedAmount = this.refundedAmount.plus(refund);
        return refund;
    }

    /**
     * 반품 진행 중 라인을 반품으로 완결한다. 전 라인이 종결(취소·반품)되면 주문을 환불로 수렴시키고 참을 반환한다.
     *
     * @throws OrderStatusException 라인이 반품 진행 중이 아니면
     * @throws OrderLineNotFoundException 라인이 없으면
     */
    public boolean completeLineReturn(UUID lineId, Instant now) {
        OrderLine line = findLine(lineId);
        if (line.getStatus() != OrderLineStatus.RETURNING) {
            throw new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        line.completeReturn();
        // 전 라인 종결이면 환불로 수렴한다 — 혼합 종결의 마지막은 항상 반품 경로다(취소 수렴은 출고 전).
        boolean allSettled = lines.stream()
                .allMatch(candidate -> candidate.getStatus() == OrderLineStatus.CANCELLED
                        || candidate.getStatus() == OrderLineStatus.RETURNED);
        if (allSettled) {
            this.status = OrderStatus.REFUNDED;
            this.refundedAt = now;
            this.refundReason = line.getReturnReason();
        }
        return allSettled;
    }

    /** 대상 라인을 찾고 없으면 거부한다. */
    private OrderLine findLine(UUID lineId) {
        return lines.stream()
                .filter(line -> line.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new OrderLineNotFoundException(OrderErrorCode.ORDER_LINE_NOT_FOUND));
    }

    /**
     * 라인 환불액을 계산한다 — 환불 미확정 라인이 마지막이면 결제 잔액 전액(끝전·배송비 흡수), 아니면 라인
     * 금액에서 안분 할인을 뺀 값이다. 취소·반품 경로가 공유한다.
     */
    private Money computeLineRefund(OrderLine line) {
        boolean last = lines.stream()
                        .filter(candidate -> candidate.getRefundAmount() == null)
                        .count()
                == 1;
        return last ? payAmount.minus(refundedAmount) : line.lineAmount().minus(discountShare(line));
    }

    /** 쿠폰 할인의 라인 금액 비례 안분(내림)을 계산한다. 끝전은 마지막 라인의 잔액 전액 환불이 흡수한다. */
    private Money discountShare(OrderLine line) {
        return Money.of(discountAmount.amount() * line.lineAmount().amount() / totalAmount.amount());
    }

    public long getVersion() {
        return version;
    }
}
```

- [ ] **Step 5: 컴파일 확인(에러 예상)**

Run: `./gradlew :module-domains:domain-order:compileJava`
Expected: FAIL — `DefaultOrderModifier`가 옛 `Order` 시그니처(`cancel(reason, now)` 등)를 호출해 컴파일 에러. 이후 Step에서 해소한다.

### Step D — OrderTest.java 전면 재작성

- [ ] **Step 6: OrderTest.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.order.domain.exception.InvalidOrderException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderLineNotFoundException;
import com.commerce.domain.order.domain.exception.OrderStatusException;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");

    private OrderLineSnapshot line(long price, int quantity) {
        return new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(price), quantity);
    }

    private Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private Order place(Money discount, Money shippingFee, @org.jspecify.annotations.Nullable UUID couponId) {
        return Order.place(UUID.randomUUID(), List.of(line(10000L, 2)), address(), discount, shippingFee, couponId);
    }

    private Order paidOrder() {
        Order order = place(Money.ZERO, Money.of(3000L), null);
        order.markPaid(NOW);
        return order;
    }

    @Test
    @DisplayName("생성 시 금액을 자기 계산하고 PENDING이다")
    void placeComputesAmounts() {
        Order order = place(Money.ZERO, Money.of(3000L), null);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(20000L));
        assertThat(order.getPayAmount()).isEqualTo(Money.of(23000L));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderNumber()).isNotBlank();
    }

    @Test
    @DisplayName("쿠폰 할인이 있으면 payAmount에서 차감된다")
    void placeWithCoupon() {
        Order order = place(Money.of(5000L), Money.of(3000L), UUID.randomUUID());
        assertThat(order.getPayAmount()).isEqualTo(Money.of(18000L));
        assertThat(order.getIssuedCouponId()).isNotNull();
    }

    @Test
    @DisplayName("라인이 없으면 거부한다")
    void placeRejectsEmpty() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), List.of(), address(), Money.ZERO, Money.ZERO, null))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("할인이 주문 금액을 초과하면 거부한다")
    void placeRejectsDiscountExceedingTotal() {
        assertThatThrownBy(() -> place(Money.of(25000L), Money.ZERO, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("쿠폰과 할인액의 존재가 불일치하면 거부한다")
    void placeRejectsCouponDiscountMismatch() {
        assertThatThrownBy(() -> place(Money.ZERO, Money.ZERO, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> place(Money.of(5000L), Money.ZERO, null)).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("결제 완료는 PAID로 전이한다")
    void markPaidTransitions() {
        Order order = paidOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PENDING이 아니면 결제 완료할 수 없다")
    void markPaidRejectsNonPending() {
        assertThatThrownBy(() -> paidOrder().markPaid(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문에 재고 차감 완료를 기록한다")
    void markStockDeductedRecordsEvidence() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        assertThat(order.getStockDeductedAt()).isNull();
        order.markStockDeducted(NOW);
        assertThat(order.getStockDeductedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING이 아니면 재고 차감 완료를 기록할 수 없다")
    void markStockDeductedRejectsNonPending() {
        assertThatThrownBy(() -> paidOrder().markStockDeducted(NOW)).isInstanceOf(OrderStatusException.class);
        Order cancelled = place(Money.ZERO, Money.ZERO, null);
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.NOT_STARTED, NOW);
        assertThatThrownBy(() -> cancelled.markStockDeducted(NOW)).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("PENDING 주문을 취소한다")
    void cancelPendingOrder() {
        Order order = place(Money.ZERO, Money.ZERO, null);
        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.NOT_STARTED, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("출고 이후 주문은 취소할 수 없다")
    void cancelRejectedAfterShipped() {
        Order order = paidOrder();
        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("전액 할인이면 payAmount는 배송비뿐이고, 배송비도 0이면 payAmount는 0이다")
    void payAmountEdges() {
        assertThat(place(Money.of(20000L), Money.of(3000L), UUID.randomUUID()).getPayAmount())
                .isEqualTo(Money.of(3000L));
        assertThat(place(Money.of(20000L), Money.ZERO, UUID.randomUUID()).getPayAmount())
                .isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("결제 완료 주문은 준비·보류 상태에서 취소할 수 있다")
    void cancelPaidBeforeShip() {
        Order preparing = paidOrder();
        preparing.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThat(preparing.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order onHold = paidOrder();
        onHold.cancel(CancellationReason.STOCK_SHORTAGE, FulfillmentStatus.ON_HOLD, NOW);
        assertThat(onHold.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 개시는 마커를 남기고 재개시는 아무것도 하지 않는다")
    void requestCancellationSetsMarkerOnce() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.PREPARING, NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);

        order.requestCancellation(FulfillmentStatus.PREPARING, NOW.plusSeconds(60));
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("보류 중 주문도 취소를 개시할 수 있다")
    void requestCancellationAllowsOnHold() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.ON_HOLD, NOW);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("미결제·취소된·출고된 주문은 취소를 개시할 수 없다")
    void requestCancellationRejectsIneligibleStates() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(() -> pending.requestCancellation(FulfillmentStatus.NOT_STARTED, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order cancelled = paidOrder();
        cancelled.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> cancelled.requestCancellation(FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order shipped = paidOrder();
        assertThatThrownBy(() -> shipped.requestCancellation(FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소 개시된 주문의 취소는 완결할 수 있다")
    void cancelCompletesWhileCancellationRequested() {
        Order order = paidOrder();
        order.requestCancellation(FulfillmentStatus.PREPARING, NOW);

        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("배송 완료 주문을 환불하면 REFUNDED가 된다")
    void refundDeliveredOrder() {
        Order order = paidOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAt()).isNotNull();
        assertThat(order.getRefundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
    }

    @Test
    @DisplayName("배송 완료 전(준비·출고) 주문은 환불할 수 없다")
    void refundRejectedBeforeDelivery() {
        assertThatThrownBy(() -> paidOrder().refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);
        assertThatThrownBy(() -> paidOrder().refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("취소된 주문은 환불할 수 없다")
    void refundRejectedForCancelledOrder() {
        Order order = paidOrder();
        order.cancel(CancellationReason.CUSTOMER_REQUEST, FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불은 1회만 유효하고 환불된 주문은 취소도 할 수 없다")
    void refundIsOneShotAndBlocksCancel() {
        Order order = paidOrder();
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
        assertThatThrownBy(() -> order.cancel(CancellationReason.ADMIN_ACTION, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("배송 완료 결제 주문에 반품을 요청하면 REQUESTED와 사유·시각이 기록된다")
    void requestReturnRecordsRequest() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("배송 완료 아닌(준비·출고·미결제) 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsUndelivered() {
        assertThatThrownBy(
                        () -> paidOrder().requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.PREPARING, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_ALLOWED));

        assertThatThrownBy(
                        () -> paidOrder().requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.SHIPPED, NOW))
                .isInstanceOf(OrderStatusException.class);

        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(
                        () -> pending.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.NOT_STARTED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("환불 완료된 주문은 반품을 요청할 수 없다")
    void requestReturnRejectsRefundedOrder() {
        Order order = paidOrder();
        order.refund(RefundReason.CS_MANUAL, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("반품 요청 중 재요청은 거부된다")
    void requestReturnRejectsDuplicate() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(
                        () -> order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED));
    }

    @Test
    @DisplayName("반품 거절은 REJECTED로 전이하고 주문은 PAID로 남는다")
    void rejectReturnKeepsOrderState() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("요청 상태가 아니면 반품을 거절할 수 없다")
    void rejectReturnRequiresRequested() {
        Order order = paidOrder();
        assertThatThrownBy(order::rejectReturn)
                .isInstanceOfSatisfying(
                        OrderStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.RETURN_NOT_REQUESTED));

        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        assertThatThrownBy(order::rejectReturn).isInstanceOf(OrderStatusException.class);
    }

    @Test
    @DisplayName("거절 후 재요청은 허용된다")
    void requestReturnAllowedAfterRejection() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW.plusSeconds(60));
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(order.getReturnReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);
        assertThat(order.getReturnRequestedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("반품 요청된 주문이 환불되면 반품은 COMPLETED로 완결된다")
    void refundCompletesRequestedReturn() {
        Order order = paidOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThat(order.getReturnStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    // 동일 금액 3라인(각 10,000)·할인 1,000·배송비 0 — 안분에 끝전(1000/3)이 생기는 결제 완료 주문.
    private Order paidThreeLineDiscountedOrder() {
        Order order = Order.place(
                UUID.randomUUID(),
                List.of(line(10000L, 1), line(10000L, 1), line(10000L, 1)),
                address(),
                Money.of(1000L),
                Money.ZERO,
                UUID.randomUUID());
        order.markPaid(NOW);
        return order;
    }

    private static List<UUID> lineIds(Order order) {
        return order.getLines().stream().map(OrderLine::getId).sorted().toList();
    }

    @Test
    @DisplayName("순차 전 라인 취소의 안분 할인 합계가 총할인과 일치하고 마지막 라인 환불이 결제 잔액 전액이다")
    void lineRefundsProrateDiscountExactly() {
        Order order = paidThreeLineDiscountedOrder();
        List<UUID> ids = lineIds(order);

        Money first = order.beginLineCancellation(ids.get(0), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(0), NOW);
        Money second = order.beginLineCancellation(ids.get(1), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(1), NOW);
        Money third = order.beginLineCancellation(ids.get(2), FulfillmentStatus.PREPARING);
        order.completeLineCancellation(ids.get(2), NOW);

        // 각 라인 할인 = 라인 금액 − 환불액. 내림 안분 333·333에 끝전 334가 마지막 잔액 환불로 흡수된다.
        assertThat(first).isEqualTo(Money.of(9667L));
        assertThat(second).isEqualTo(Money.of(9667L));
        assertThat(third).isEqualTo(Money.of(9666L));
        long discountSum = 30000L - first.amount() - second.amount() - third.amount();
        assertThat(discountSum).isEqualTo(1000L);
        assertThat(order.getRefundedAmount()).isEqualTo(order.getPayAmount());
    }

    @Test
    @DisplayName("부분 취소 후 잔여 환불 누계·라인 상태가 정확하고 주문은 결제 완료로 남는다")
    void beginAndCompleteCancelSingleLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        Money refund = order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        assertThat(refund).isEqualTo(Money.of(9667L));
        assertThat(order.getRefundedAmount()).isEqualTo(Money.of(9667L));
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.CANCELLING);
        assertThat(lineOf(order, lineId).getRefundAmount()).isEqualTo(Money.of(9667L));

        boolean converged = order.completeLineCancellation(lineId, NOW);

        assertThat(converged).isFalse();
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("마지막 잔여 라인 취소 완결 시 주문이 전체 취소로 수렴한다")
    void lastLineCancellationConvergesToFullCancellation() {
        Order order =
                Order.place(UUID.randomUUID(), List.of(line(10000L, 1)), address(), Money.ZERO, Money.of(3000L), null);
        order.markPaid(NOW);
        UUID lineId = lineIds(order).get(0);

        Money refund = order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        boolean converged = order.completeLineCancellation(lineId, NOW);

        assertThat(refund).isEqualTo(Money.of(13000L));
        assertThat(converged).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
    }

    @Test
    @DisplayName("라인 취소 개시는 결제 완료·출고 전 주문의 주문됨 라인에서만 허용된다")
    void beginLineCancellationGuards() {
        Order pending = place(Money.ZERO, Money.ZERO, null);
        assertThatThrownBy(
                        () -> pending.beginLineCancellation(lineIds(pending).get(0), FulfillmentStatus.NOT_STARTED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        Order shipped = paidOrder();
        assertThatThrownBy(
                        () -> shipped.beginLineCancellation(lineIds(shipped).get(0), FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_NOT_ALLOWED);

        Order cancelRequested = paidOrder();
        cancelRequested.requestCancellation(FulfillmentStatus.PREPARING, NOW);
        assertThatThrownBy(() -> cancelRequested.beginLineCancellation(
                        lineIds(cancelRequested).get(0), FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS);

        Order order = paidThreeLineDiscountedOrder();
        assertThatThrownBy(() -> order.beginLineCancellation(UUID.randomUUID(), FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderLineNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_LINE_NOT_FOUND);

        UUID lineId = lineIds(order).get(0);
        order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
        order.completeLineCancellation(lineId, NOW);
        assertThatThrownBy(() -> order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("취소 진행 중이 아닌 라인의 완결은 거부된다")
    void completeLineCancellationRequiresCancellingLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        assertThatThrownBy(() -> order.completeLineCancellation(lineId, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("부분 취소 이력이 있는 주문의 전체 취소 개시는 거부된다")
    void requestCancellationRejectsPartiallyCancelledOrder() {
        Order order = paidThreeLineDiscountedOrder();
        order.beginLineCancellation(lineIds(order).get(0), FulfillmentStatus.PREPARING);

        assertThatThrownBy(() -> order.requestCancellation(FulfillmentStatus.PREPARING, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("불균등 금액·복수 수량 라인의 안분 합계도 총할인과 일치한다")
    void unevenLineRefundsProrateDiscountExactly() {
        // 7000×1 + 6000×2 + 3100×1 = 22100, 할인 1000 — 안분 316·542에 끝전이 남는 조합.
        Order order = Order.place(
                UUID.randomUUID(),
                List.of(line(7000L, 1), line(6000L, 2), line(3100L, 1)),
                address(),
                Money.of(1000L),
                Money.ZERO,
                UUID.randomUUID());
        order.markPaid(NOW);
        List<UUID> ids = lineIds(order);

        long refundSum = 0;
        for (UUID lineId : ids) {
            refundSum += order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING).amount();
            order.completeLineCancellation(lineId, NOW);
        }

        assertThat(refundSum).isEqualTo(order.getPayAmount().amount());
        assertThat(22100L - refundSum).isEqualTo(1000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("부분 취소 이력이 있는 주문의 전체 반품 환불은 거부된다")
    void refundRejectsPartiallyCancelledOrder() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);
        order.beginLineCancellation(lineId, FulfillmentStatus.PREPARING);
        order.completeLineCancellation(lineId, NOW);

        assertThatThrownBy(() -> order.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);
    }

    @Test
    @DisplayName("라인 반품 요청·승인이 공유 산식으로 환불액을 확정하고 라인을 RETURNED로 완결한다")
    void lineReturnRequestAndApprovalConfirmsRefund() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);

        order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);

        Money refund = order.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
        assertThat(refund).isEqualTo(Money.of(9667L));
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURNING);
        assertThat(order.getRefundedAmount()).isEqualTo(Money.of(9667L));

        boolean converged = order.completeLineReturn(lineId, NOW);

        assertThat(converged).isFalse();
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURNED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("라인 반품 요청은 배송 완료된 주문의 주문됨 라인에서만 허용된다")
    void requestLineReturnGuards() {
        Order paid = paidThreeLineDiscountedOrder();
        assertThatThrownBy(() -> paid.requestLineReturn(
                        lineIds(paid).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.PREPARING))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_NOT_ALLOWED);

        Order order = paidThreeLineDiscountedOrder();
        UUID returned = lineIds(order).get(0);
        order.requestLineReturn(returned, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        order.beginLineReturn(returned, FulfillmentStatus.DELIVERED);
        order.completeLineReturn(returned, NOW);
        assertThatThrownBy(() -> order.requestLineReturn(
                        returned, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        UUID requested = lineIds(order).get(1);
        order.requestLineReturn(requested, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() -> order.requestLineReturn(
                        requested, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        Order mixed = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(mixed).get(0);
        mixed.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        mixed.completeLineCancellation(cancelled, NOW);
        assertThatThrownBy(() -> mixed.requestLineReturn(
                        cancelled, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);

        Order fullReturn = paidThreeLineDiscountedOrder();
        fullReturn.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        assertThatThrownBy(() -> fullReturn.requestLineReturn(
                        lineIds(fullReturn).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("전 라인 반품 완결 시 주문이 REFUNDED로 수렴하고 혼합(취소+반품) 종결도 REFUNDED다")
    void allLineReturnsConvergeToRefunded() {
        Order order = paidThreeLineDiscountedOrder();
        for (UUID lineId : lineIds(order)) {
            order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
            order.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
            order.completeLineReturn(lineId, NOW);
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAmount()).isEqualTo(order.getPayAmount());
        assertThat(order.getRefundedAt()).isEqualTo(NOW);
        assertThat(order.getRefundReason()).isEqualTo(RefundReason.PRODUCT_DEFECT);

        Order mixed = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(mixed).get(0);
        mixed.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        mixed.completeLineCancellation(cancelled, NOW);
        for (UUID lineId : List.of(lineIds(mixed).get(1), lineIds(mixed).get(2))) {
            mixed.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);
            mixed.beginLineReturn(lineId, FulfillmentStatus.DELIVERED);
            mixed.completeLineReturn(lineId, NOW);
        }
        assertThat(mixed.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(mixed.getRefundedAmount()).isEqualTo(mixed.getPayAmount());
    }

    @Test
    @DisplayName("라인 반품 거절은 라인을 주문됨으로 되돌리고 재요청이 가능하다")
    void rejectLineReturnRestoresOrderedLine() {
        Order order = paidThreeLineDiscountedOrder();
        UUID lineId = lineIds(order).get(0);
        order.requestLineReturn(lineId, RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);

        order.rejectLineReturn(lineId);

        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.ORDERED);
        assertThat(lineOf(order, lineId).getReturnReason()).isNull();
        order.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);
        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
    }

    @Test
    @DisplayName("전체 반품 요청은 부분 환불 이력·라인 반품 진행 중이면 거부된다")
    void requestReturnRejectsPartialHistory() {
        Order partiallyCancelled = paidThreeLineDiscountedOrder();
        UUID cancelled = lineIds(partiallyCancelled).get(0);
        partiallyCancelled.beginLineCancellation(cancelled, FulfillmentStatus.PREPARING);
        partiallyCancelled.completeLineCancellation(cancelled, NOW);
        assertThatThrownBy(() -> partiallyCancelled.requestReturn(
                        RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.PARTIALLY_CANCELLED);

        Order lineReturning = paidThreeLineDiscountedOrder();
        lineReturning.requestLineReturn(
                lineIds(lineReturning).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() -> lineReturning.requestReturn(
                        RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("라인 이력이 있는 주문의 전체 반품 환불 집행은 거부된다")
    void refundRejectsAnyLineHistory() {
        Order requested = paidThreeLineDiscountedOrder();
        requested.requestLineReturn(
                lineIds(requested).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        assertThatThrownBy(() -> requested.refund(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("라인 반품 승인 개시는 배송 완료된 결제 주문·전체 반품 미진행에서만 허용된다")
    void beginLineReturnGuardsOrderState() {
        // 반품 요청 라인을 만든 뒤 주문 상태가 어긋난 경우를 재현할 수 없으므로(요청 가드가 선행),
        // 전체 반품 요청과의 공존(쓰기 스큐 잔여 상태)을 직접 재현해 집행 가드를 단언한다.
        Order order = paidThreeLineDiscountedOrder();
        order.requestLineReturn(lineIds(order).get(0), RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED);
        forceFullReturnRequested(order);

        assertThatThrownBy(() -> order.beginLineReturn(lineIds(order).get(0), FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderStatusException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.RETURN_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("전체 반품 거절 후 라인 반품 요청은 허용된다")
    void lineReturnAllowedAfterFullReturnRejection() {
        Order order = paidThreeLineDiscountedOrder();
        order.requestReturn(RefundReason.PRODUCT_DEFECT, FulfillmentStatus.DELIVERED, NOW);
        order.rejectReturn();

        UUID lineId = lineIds(order).get(0);
        order.requestLineReturn(lineId, RefundReason.CHANGE_OF_MIND, FulfillmentStatus.DELIVERED);

        assertThat(lineOf(order, lineId).getStatus()).isEqualTo(OrderLineStatus.RETURN_REQUESTED);
    }

    /** 쓰기 스큐로만 도달 가능한 전체 반품 요청 공존 상태를 리플렉션으로 재현한다. */
    private static void forceFullReturnRequested(Order order) {
        try {
            var field = Order.class.getDeclaredField("returnRequest");
            field.setAccessible(true);
            field.set(order, new ReturnRequest(ReturnStatus.REQUESTED, NOW, RefundReason.PRODUCT_DEFECT));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OrderLine lineOf(Order order, UUID lineId) {
        return order.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow();
    }
}
```

- [ ] **Step 7: OrderTest 단독 실행 확인**

Run: `./gradlew :module-domains:domain-order:test --tests "com.commerce.domain.order.domain.OrderTest"`
Expected: PASS(모듈 전체는 아직 컴파일 에러 상태일 수 있으나, 도메인 패키지만 격리 컴파일하는 이 태스크는 이어지는 Step에서 application 계층을 마저 고친 뒤 전체로 재확인한다)

### Step E — FulfillmentRepository·OrderRepository

- [ ] **Step 8: FulfillmentRepository.java 작성**

```java
package com.commerce.domain.order.application.required;

import com.commerce.domain.order.domain.Fulfillment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {

    Optional<Fulfillment> findByOrderId(UUID orderId);

    List<Fulfillment> findByOrderIdIn(Collection<UUID> orderIds);
}
```

- [ ] **Step 9: OrderRepository.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.application.required;

import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.FulfillmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    /** 결제 완료·미배송(이행 행 없음도 미배송으로 취급) 주문이 있는지 본다. */
    @Query("""
            select count(o) > 0
            from Order o
            where o.memberId = :memberId
              and o.status = com.commerce.domain.order.domain.OrderStatus.PAID
              and not exists (
                  select 1 from Fulfillment f
                  where f.orderId = o.id and f.status = com.commerce.domain.order.domain.FulfillmentStatus.DELIVERED
              )
            """)
    boolean existsUndeliveredPaidByMemberId(@Param("memberId") UUID memberId);

    /** 결제 완료·배송 완료 주문에 해당 상품 라인이 있는지 확인한다. */
    // 파생 쿼리로는 라인·이행 조인 조건까지 이름이 비대해져 @Query를 쓴다(architecture.md 쿼리 선택 기준).
    // 애그리거트 간 참조는 ID만 두므로(FK 없음) 멀티 루트 JPQL로 Fulfillment를 조인한다.
    @Query("""
            select count(o) > 0
            from Order o join o.lines l, Fulfillment f
            where o.memberId = :memberId
              and l.productId = :productId
              and o.status = com.commerce.domain.order.domain.OrderStatus.PAID
              and f.orderId = o.id
              and f.status = com.commerce.domain.order.domain.FulfillmentStatus.DELIVERED
            """)
    boolean existsDeliveredLineByMemberIdAndProductId(
            @Param("memberId") UUID memberId, @Param("productId") UUID productId);

    /** 회원의 주문 ID 페이지를 최신순으로 조회한다. */
    @Query("""
            select o.id
            from Order o
            where o.memberId = :memberId
            order by o.createdAt desc, o.id desc
            """)
    Page<UUID> findIdPageByMemberId(@Param("memberId") UUID memberId, Pageable pageable);

    /**
     * 결제·이행 축 상태가 모두 일치하는 주문의 ID 페이지를 최신순으로 조회한다. 이행 행이 없으면
     * NOT_STARTED로 취급한다(PENDING 주문의 정상 상태).
     */
    @Query("""
            select o.id
            from Order o
            where o.status = :status
              and (
                  (:fulfillmentStatus = com.commerce.domain.order.domain.FulfillmentStatus.NOT_STARTED
                      and not exists (select 1 from Fulfillment f where f.orderId = o.id))
                  or exists (
                      select 1 from Fulfillment f
                      where f.orderId = o.id and f.status = :fulfillmentStatus
                  )
              )
            order by o.createdAt desc, o.id desc
            """)
    Page<UUID> findIdPageByStatusAndFulfillmentStatus(
            @Param("status") OrderStatus status,
            @Param("fulfillmentStatus") FulfillmentStatus fulfillmentStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByIdInOrderByCreatedAtDescIdDesc(Collection<UUID> ids);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant createdAt);
}
```

### Step F — OrderInfo·DefaultOrderReader·DefaultOrderModifier·OrderPaidListener

- [ ] **Step 10: OrderInfo.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.application.info;

import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.HoldReason;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.ReturnStatus;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 주문 조회 경계 모델이다. */
public record OrderInfo(
        UUID id,
        String orderNumber,
        UUID memberId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        Money totalAmount,
        Money discountAmount,
        Money shippingFee,
        Money payAmount,
        Money refundedAmount,
        @Nullable UUID issuedCouponId,
        AddressInfo shippingAddress,
        List<OrderLineInfo> lines,
        @Nullable Instant stockDeductedAt,
        @Nullable Instant paidAt,
        @Nullable Instant shippedAt,
        @Nullable String carrier,
        @Nullable String trackingNumber,
        @Nullable Instant deliveredAt,
        @Nullable Instant cancelledAt,
        @Nullable CancellationReason cancellationReason,
        @Nullable HoldReason holdReason,
        @Nullable Instant refundedAt,
        @Nullable RefundReason refundReason,
        @Nullable ReturnStatus returnStatus,
        @Nullable Instant returnRequestedAt,
        @Nullable RefundReason returnReason,
        Instant createdAt,
        Instant updatedAt) {

    public OrderInfo {
        lines = List.copyOf(lines);
    }

    /** 주문·이행 엔티티에서 조회 모델을 만든다. 이행 행이 없으면(미개시) 시작 전으로 합성한다. */
    public static OrderInfo of(Order order, @Nullable Fulfillment fulfillment) {
        return new OrderInfo(
                order.getId(),
                order.getOrderNumber(),
                order.getMemberId(),
                order.getStatus(),
                fulfillment == null ? FulfillmentStatus.NOT_STARTED : fulfillment.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getPayAmount(),
                order.getRefundedAmount(),
                order.getIssuedCouponId(),
                AddressInfo.from(order.getShippingAddress()),
                order.getLines().stream().map(OrderLineInfo::from).toList(),
                order.getStockDeductedAt(),
                order.getPaidAt(),
                fulfillment == null ? null : fulfillment.getShippedAt(),
                fulfillment == null ? null : fulfillment.getCarrier(),
                fulfillment == null ? null : fulfillment.getTrackingNumber(),
                fulfillment == null ? null : fulfillment.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                fulfillment == null ? null : fulfillment.getHoldReason(),
                order.getRefundedAt(),
                order.getRefundReason(),
                order.getReturnStatus(),
                order.getReturnRequestedAt(),
                order.getReturnReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
```

- [ ] **Step 11: DefaultOrderReader.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.application;

import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.application.required.OrderRepository;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderReader}의 기본 구현이다. */
@Service
class DefaultOrderReader implements OrderReader {

    private final OrderRepository orderRepository;
    private final FulfillmentRepository fulfillmentRepository;

    DefaultOrderReader(OrderRepository orderRepository, FulfillmentRepository fulfillmentRepository) {
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public OrderInfo getOrder(UUID orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.of(order, fulfillmentRepository.findByOrderId(orderId).orElse(null));
    }

    @Transactional(readOnly = true)
    @Override
    public OrderInfo getOrder(UUID orderId, UUID memberId) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderInfo.of(order, fulfillmentRepository.findByOrderId(orderId).orElse(null));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasUndeliveredPaidOrder(UUID memberId) {
        return orderRepository.existsUndeliveredPaidByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasDeliveredProduct(UUID memberId, UUID productId) {
        return orderRepository.existsDeliveredLineByMemberIdAndProductId(memberId, productId);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderInfo> getOrdersByMember(UUID memberId, Pageable pageable) {
        // 컬렉션 페치(lines)와 LIMIT을 한 쿼리에 섞으면 Hibernate가 전체를 로드해 메모리에서 자르므로,
        // ID 페이지를 먼저 뜨고 그 ID들만 IN으로 라인을 페치한다.
        Page<UUID> idPage = orderRepository.findIdPageByMemberId(memberId, pageable);
        return toOrderPage(idPage, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderInfo> getOrdersByStatus(
            OrderStatus status, FulfillmentStatus fulfillmentStatus, Pageable pageable) {
        Page<UUID> idPage = orderRepository.findIdPageByStatusAndFulfillmentStatus(status, fulfillmentStatus, pageable);
        return toOrderPage(idPage, pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderInfo> findPendingBefore(Instant cutoff) {
        // PENDING 주문은 이행 행이 없다 — 벌크 조회 없이 매번 NOT_STARTED로 합성한다.
        return orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff).stream()
                .map(order -> OrderInfo.of(order, null))
                .toList();
    }

    /** ID 페이지로 주문을 페치해 이행을 벌크 장식하고, 총건수를 유지한 Info 페이지로 옮긴다. */
    private Page<OrderInfo> toOrderPage(Page<UUID> idPage, Pageable pageable) {
        List<Order> orders = orderRepository.findByIdInOrderByCreatedAtDescIdDesc(idPage.getContent());
        Map<UUID, Fulfillment> fulfillmentsByOrderId = fulfillmentRepository.findByOrderIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(Fulfillment::getOrderId, Function.identity()));
        List<OrderInfo> infos = orders.stream()
                .map(order -> OrderInfo.of(order, fulfillmentsByOrderId.get(order.getId())))
                .toList();
        return new PageImpl<>(infos, pageable, idPage.getTotalElements());
    }
}
```

- [ ] **Step 12: DefaultOrderModifier.java를 아래 내용으로 전체 교체**

```java
package com.commerce.domain.order.application;

import com.commerce.common.event.publish.MessagePublisher;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.application.required.OrderRepository;
import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.HoldReason;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderLineStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.order.domain.exception.OrderNotFoundException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.event.order.OrderPaid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderModifier}의 기본 구현이다. */
@Service
class DefaultOrderModifier implements OrderModifier {

    private final OrderRepository orderRepository;
    private final FulfillmentRepository fulfillmentRepository;
    private final MessagePublisher messagePublisher;
    private final Clock clock;

    DefaultOrderModifier(
            OrderRepository orderRepository,
            FulfillmentRepository fulfillmentRepository,
            MessagePublisher messagePublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.messagePublisher = messagePublisher;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void markPaid(UUID orderId) {
        Order order = find(orderId);
        order.markPaid(clock.instant());
        messagePublisher.publish(new OrderPaid(order.getId(), order.getMemberId(), order.getOrderedVariantIds()));
    }

    @Transactional
    @Override
    public void markStockDeducted(UUID orderId) {
        find(orderId).markStockDeducted(clock.instant());
    }

    @Transactional
    @Override
    public void cancel(UUID orderId, CancellationReason reason) {
        find(orderId).cancel(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public Money beginLineCancellation(UUID orderId, UUID lineId) {
        return find(orderId).beginLineCancellation(lineId, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public boolean completeLineCancellation(UUID orderId, UUID lineId) {
        return find(orderId).completeLineCancellation(lineId, clock.instant());
    }

    @Transactional
    @Override
    public void requestCancellation(UUID orderId) {
        find(orderId).requestCancellation(fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void refund(UUID orderId, RefundReason reason) {
        find(orderId).refund(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void requestReturn(UUID orderId, UUID memberId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestReturn(reason, fulfillmentStatusOf(orderId), clock.instant());
    }

    @Transactional
    @Override
    public void requestLineReturn(UUID orderId, UUID memberId, UUID lineId, RefundReason reason) {
        Order order = orderRepository
                .findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
        order.requestLineReturn(lineId, reason, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public void rejectLineReturn(UUID orderId, UUID lineId) {
        find(orderId).rejectLineReturn(lineId);
    }

    @Transactional
    @Override
    public Money beginLineReturn(UUID orderId, UUID lineId) {
        return find(orderId).beginLineReturn(lineId, fulfillmentStatusOf(orderId));
    }

    @Transactional
    @Override
    public boolean completeLineReturn(UUID orderId, UUID lineId) {
        return find(orderId).completeLineReturn(lineId, clock.instant());
    }

    @Transactional
    @Override
    public void rejectReturn(UUID orderId) {
        find(orderId).rejectReturn();
    }

    @Transactional
    @Override
    public void ship(UUID orderId, String carrier, String trackingNumber) {
        Order order = find(orderId);
        boolean cancelInProgress = order.getCancelRequestedAt() != null
                || order.getLines().stream().anyMatch(line -> line.getStatus() == OrderLineStatus.CANCELLING);
        requireFulfillment(orderId, order).ship(carrier, trackingNumber, cancelInProgress, clock.instant());
    }

    @Transactional
    @Override
    public void confirmDelivery(UUID orderId) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).confirmDelivery(clock.instant());
    }

    @Transactional
    @Override
    public void holdFulfillment(UUID orderId, HoldReason reason) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).hold(reason);
    }

    @Transactional
    @Override
    public void releaseFulfillment(UUID orderId) {
        Order order = find(orderId);
        requireFulfillment(orderId, order).release();
    }

    /** 주문을 찾고 없으면 거부한다. */
    private Order find(UUID orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /** 주문의 이행 축 상태를 읽는다. 이행 행이 없으면 시작 전이다(PENDING 주문의 정상 상태). */
    private FulfillmentStatus fulfillmentStatusOf(UUID orderId) {
        return fulfillmentRepository
                .findByOrderId(orderId)
                .map(Fulfillment::getStatus)
                .orElse(FulfillmentStatus.NOT_STARTED);
    }

    /** 결제 완료 주문의 이행 애그리거트를 찾는다. 결제 미완료거나 생성 반영 전이면 거부한다. */
    private Fulfillment requireFulfillment(UUID orderId, Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new FulfillmentStatusException(OrderErrorCode.NOT_PAID);
        }
        return fulfillmentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new FulfillmentStatusException(OrderErrorCode.FULFILLMENT_NOT_READY));
    }
}
```

- [ ] **Step 13: OrderPaidListenerTest.java 작성 (구현보다 먼저)**

```java
package com.commerce.domain.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.event.order.OrderPaid;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** {@link OrderPaid} 소비자의 이행 생성 행동을 실 PostgreSQL 영속 슬라이스로 검증하는 테스트다. */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/ordering",
            "spring.flyway.schemas=ordering",
            "spring.flyway.default-schema=ordering"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(OrderPaidListener.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderPaidListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final OrderPaidListener listener;
    private final FulfillmentRepository fulfillmentRepository;

    OrderPaidListenerTest(OrderPaidListener listener, FulfillmentRepository fulfillmentRepository) {
        this.listener = listener;
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Test
    @DisplayName("OrderPaid를 소비해 이행을 PREPARING으로 생성한다")
    void consumeCreatesPreparingFulfillment() {
        UUID orderId = UUID.randomUUID();

        listener.on(new OrderPaid(orderId, UUID.randomUUID(), Set.of(UUID.randomUUID())));

        assertThat(fulfillmentRepository.findByOrderId(orderId))
                .isPresent()
                .get()
                .satisfies(fulfillment -> assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING));
    }

    @Test
    @DisplayName("중복 이벤트 소비는 1회 생성 효과로 멱등하다")
    void duplicateConsumptionIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        OrderPaid event = new OrderPaid(orderId, UUID.randomUUID(), Set.of(UUID.randomUUID()));

        listener.on(event);
        listener.on(event);

        assertThat(fulfillmentRepository.findByOrderId(orderId)).isPresent();
        assertThat(fulfillmentRepository.count()).isEqualTo(1);
    }
}
```

- [ ] **Step 14: 컴파일해 실패 확인**

Run: `./gradlew :module-domains:domain-order:compileTestJava`
Expected: FAIL — `OrderPaidListener` cannot be resolved (아직 존재하지 않음)

- [ ] **Step 15: OrderPaidListener.java 구현**

```java
package com.commerce.domain.order.application;

import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.domain.Fulfillment;
import com.commerce.event.order.OrderPaid;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderPaid}를 소비해 이행 애그리거트를 준비 중으로 생성하는 소비자다.
 *
 * <p>아웃박스 릴레이가 커밋된 이벤트만 재발행하므로 커밋 후 단계 대기 없이 평 리스너로 소비하고, 소비
 * 쓰기는 자체 트랜잭션으로 커밋한다. 재전달(at-least-once)은 존재 확인 후 생성으로 멱등을 지킨다.
 */
@Component
class OrderPaidListener {

    private final FulfillmentRepository fulfillmentRepository;

    OrderPaidListener(FulfillmentRepository fulfillmentRepository) {
        this.fulfillmentRepository = fulfillmentRepository;
    }

    /** 결제 완료 이벤트를 받아 이행을 준비 중으로 생성한다. 이미 생성됐으면 아무것도 하지 않는다. */
    @EventListener
    @Transactional
    public void on(OrderPaid event) {
        if (fulfillmentRepository.findByOrderId(event.orderId()).isPresent()) {
            return;
        }
        fulfillmentRepository.save(Fulfillment.create(event.orderId()));
    }
}
```

- [ ] **Step 16: OrderPaidListenerTest 실행해 통과 확인**

Run: `./gradlew :module-domains:domain-order:test --tests "com.commerce.domain.order.application.OrderPaidListenerTest"`
Expected: PASS (2 tests)

### Step G — OrderPersistenceTest·FulfillmentPersistenceTest

- [ ] **Step 17: OrderPersistenceTest.java에서 이행 시나리오 제거**

`module-domains/domain-order/src/test/java/com/commerce/domain/order/application/OrderPersistenceTest.java`에서 `fulfillmentFlowPersists()` 테스트 메서드 전체(라인 113~131, `@Test` 애노테이션부터 닫는 `}`까지)를 삭제한다. 클래스 Javadoc의 "이행 전이를 확인한다"도 제거한다:

```java
/**
 * order 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>두 테이블 {@code ddl-auto=validate} 정합, Address 임베디드·Money 컬럼 왕복, 라인 캐스케이드를 확인한다.
 */
```

(`fulfillmentFlowPersists` 삭제 후 `placeThenGetOrder`·`cancelPersistsAndIncrementsVersion`·`getOrdersByMemberBreaksCreatedAtTieWithIdDesc` 3개 테스트만 남는다. 나머지 코드는 그대로 둔다 — `fulfillmentStatus()==NOT_STARTED` 단언은 이행 행 부재 시 합성 로직으로 그대로 성립하므로 수정하지 않는다.)

- [ ] **Step 18: FulfillmentPersistenceTest.java 작성**

```java
package com.commerce.domain.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.common.event.publish.MessagePublisher;
import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.provided.OrderAppender;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderLineSnapshot;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.shared.entity.Money;
import com.commerce.event.order.OrderPaid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 이행 애그리거트의 생성 조율·전이·조회 합성을 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code OrderPaid} 소비는 아웃박스 릴레이 경유가 아니라 리스너를 직접 호출해 시뮬레이션한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/ordering",
            "spring.flyway.schemas=ordering",
            "spring.flyway.default-schema=ordering"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({
    DefaultOrderAppender.class,
    DefaultOrderReader.class,
    DefaultOrderModifier.class,
    OrderPaidListener.class,
    FulfillmentPersistenceTest.NoOpMessagingConfig.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FulfillmentPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final OrderPaidListener orderPaidListener;
    private final TestEntityManager em;

    FulfillmentPersistenceTest(
            OrderAppender orderAppender,
            OrderReader orderReader,
            OrderModifier orderModifier,
            OrderPaidListener orderPaidListener,
            TestEntityManager em) {
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.orderPaidListener = orderPaidListener;
        this.em = em;
    }

    private UUID place() {
        List<OrderLineSnapshot> lines = List.of(
                new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(10000L), 2));
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(UUID.randomUUID(), lines, address, Money.ZERO, Money.of(3000L), null);
    }

    /** 결제 완료하고 이행 생성 리스너를 직접 호출해 PREPARING 이행을 만든다. */
    private UUID paidOrderWithFulfillment() {
        UUID orderId = place();
        em.flush();
        orderModifier.markPaid(orderId);
        em.flush();
        OrderInfo order = orderReader.getOrder(orderId);
        orderPaidListener.on(new OrderPaid(orderId, order.memberId(), Set.of()));
        em.flush();
        return orderId;
    }

    @Test
    @DisplayName("결제·출고·배송 완료 이행이 반영되고 운송장 기록이 왕복한다")
    void fulfillmentFlowPersists() {
        UUID orderId = paidOrderWithFulfillment();

        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        em.flush();
        orderModifier.confirmDelivery(orderId);
        em.flush();
        em.clear();

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.carrier()).isEqualTo("CJ대한통운");
        assertThat(order.trackingNumber()).isEqualTo("688900123456");
    }

    @Test
    @DisplayName("결제 완료 전 주문의 출고는 거부된다")
    void shipRejectsUnpaidOrder() {
        UUID orderId = place();
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.NOT_PAID));
    }

    @Test
    @DisplayName("결제는 완료됐으나 이행 생성이 아직 반영되지 않은 주문의 출고는 거부된다")
    void shipRejectsBeforeFulfillmentCreated() {
        // OrderPaidListener를 의도적으로 호출하지 않아 markPaid 커밋과 이행 생성 사이 레이스 창을 재현한다.
        UUID orderId = place();
        em.flush();
        orderModifier.markPaid(orderId);
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.FULFILLMENT_NOT_READY));
    }

    @Test
    @DisplayName("라인 취소 진행 중인 주문의 출고는 거부된다")
    void shipRejectsWhileLineCancellationInProgress() {
        UUID orderId = paidOrderWithFulfillment();
        OrderInfo order = orderReader.getOrder(orderId);
        UUID lineId = order.lines().get(0).id();
        orderModifier.beginLineCancellation(orderId, lineId);
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
    }

    @TestConfiguration
    static class NoOpMessagingConfig {

        @Bean
        MessagePublisher messagePublisher() {
            return event -> {};
        }
    }
}
```

- [ ] **Step 19: domain-order 모듈 전체 빌드**

Run: `./gradlew :module-domains:domain-order:build`
Expected: PASS — 컴파일·Spotless·NullAway·Error Prone·전체 테스트(`OrderTest`·`FulfillmentTest`·`OrderPaidListenerTest`·`OrderPersistenceTest`·`FulfillmentPersistenceTest`) 통과.

만약 실패하면: 컴파일 에러는 이전 Step들의 시그니처 불일치를 우선 점검한다(특히 `find(orderId)` 재사용 시 `Order` 지역변수 섀도잉). 테스트 실패는 어떤 시나리오인지 확인 후 해당 Step의 코드를 수정한다 — 새 코드를 추가하지 않고 기존 Step 내용을 고친다.

- [ ] **Step 20: Commit**

```bash
git add module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/exception/OrderErrorCode.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Cancellation.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/ReturnRequest.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/domain/Order.java \
        module-domains/domain-order/src/test/java/com/commerce/domain/order/domain/OrderTest.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/required/FulfillmentRepository.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/required/OrderRepository.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/DefaultOrderModifier.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/info/OrderInfo.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/DefaultOrderReader.java \
        module-domains/domain-order/src/main/java/com/commerce/domain/order/application/OrderPaidListener.java \
        module-domains/domain-order/src/test/java/com/commerce/domain/order/application/OrderPaidListenerTest.java \
        module-domains/domain-order/src/test/java/com/commerce/domain/order/application/OrderPersistenceTest.java \
        module-domains/domain-order/src/test/java/com/commerce/domain/order/application/FulfillmentPersistenceTest.java
git commit -m "refactor: Order 이행축 제거·취소반품축 값객체화, Fulfillment 애플리케이션 계층 컷오버"
```

---

## Task 4: query-order 크로스 애그리거트 조인 반영

`DefaultOrderSearchReader`가 `QOrder.fulfillmentStatus`를 직접 프로젝션하던 것을 `QFulfillment` LEFT JOIN + coalesce로 바꾼다. 이행 행이 없는 주문(PENDING 등)도 그대로 검색 결과에 남아야 하므로 반드시 LEFT JOIN이어야 한다.

**Files:**
- Modify: `module-query/query-order/src/main/java/com/commerce/query/order/DefaultOrderSearchReader.java`

**Interfaces:**
- Consumes: Task 1의 `Fulfillment`(및 그 QueryDSL 생성 타입 `QFulfillment`, Gradle annotationProcessor가 컴파일 시 자동 생성).
- Produces: `OrderSearchReader.getMemberOrderPage(...)` — 시그니처·반환 `OrderSearchInfo` 형태 무변경.

- [ ] **Step 1: DefaultOrderSearchReader.java 수정**

`module-query/query-order/src/main/java/com/commerce/query/order/DefaultOrderSearchReader.java`을 아래 내용으로 전체 교체:

```java
package com.commerce.query.order;

import static com.commerce.domain.member.domain.QMember.member;
import static com.commerce.domain.order.domain.QFulfillment.fulfillment;
import static com.commerce.domain.order.domain.QOrder.order;

import com.commerce.domain.member.domain.Email;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrderSearchReader}의 기본 구현이다. */
@Service
class DefaultOrderSearchReader implements OrderSearchReader {

    private final JPAQueryFactory queryFactory;

    DefaultOrderSearchReader(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderSearchInfo> getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable) {
        Email emailValue = Email.of(email);

        // UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 주문 우선 정렬을 겸한다. 활성 회원 필터는
        // 소프트삭제 조회 기본(삭제 미포함)이자 부분 유니크 인덱스(ux_member_email_active)의 사용 조건이다.
        // 이행은 Order와 별도 애그리거트라 LEFT JOIN한다 — 이행 미개시(행 없음) 주문도 검색 결과에 남아야
        // 하고, 그 경우 NOT_STARTED로 합성한다(코드상 fulfillmentStatus 컬럼의 기존 기본값과 동일 의미).
        List<OrderSearchInfo> content = queryFactory
                .select(Projections.constructor(
                        OrderSearchInfo.class,
                        order.id,
                        order.orderNumber,
                        member.id,
                        // 이메일 등치 필터로 모든 행의 이메일이 입력값과 같다 — 컨버터 타입(Email) 경로 대신 상수로 채운다.
                        Expressions.constant(email),
                        order.status,
                        fulfillment.status.coalesce(FulfillmentStatus.NOT_STARTED),
                        order.payAmount,
                        order.createdAt))
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .leftJoin(fulfillment)
                .on(fulfillment.orderId.eq(order.id))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .join(member)
                .on(member.id.eq(order.memberId))
                .where(member.email.eq(emailValue), member.deletedAt.isNull(), statusEq(status))
                .fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** 상태 조건이 있으면 등치 식을, 없으면 null을 반환한다 — where는 null 조건을 무시한다. */
    private static @Nullable BooleanExpression statusEq(@Nullable OrderStatus status) {
        return status == null ? null : order.status.eq(status);
    }
}
```

- [ ] **Step 2: query-order 모듈 빌드**

Run: `./gradlew :module-query:query-order:build`
Expected: PASS — 기존 `OrderSearchPersistenceTest`의 4개 시나리오가 그대로 통과한다(이 테스트는 `order.markPaid()`를 엔티티로 직접 호출해 `Fulfillment` 행이 생기지 않으므로, LEFT JOIN+coalesce 경로를 실제로 거친다).

- [ ] **Step 3: Commit**

```bash
git add module-query/query-order/src/main/java/com/commerce/query/order/DefaultOrderSearchReader.java
git commit -m "refactor: 관리자 주문 검색이 Fulfillment를 LEFT JOIN하도록 전환"
```

---

## Task 5: contract 마이그레이션 — orders 이행 컬럼 제거

Task 3까지 완료되면 `Order`·`OrderInfo`·query-order 어디도 `orders` 테이블의 이행축 6개 컬럼을 더는 참조하지 않는다. 이제 물리적으로 제거한다.

**Files:**
- Create: `module-domains/domain-order/src/main/resources/db/migration/ordering/V5__drop_fulfillment_columns_from_orders.sql`

- [ ] **Step 1: 마이그레이션 파일 작성**

```sql
-- contract
ALTER TABLE ordering.orders
    DROP COLUMN fulfillment_status,
    DROP COLUMN shipped_at,
    DROP COLUMN carrier,
    DROP COLUMN tracking_number,
    DROP COLUMN delivered_at,
    DROP COLUMN hold_reason;
```

(컬럼을 참조하던 복합 인덱스 `ix_orders_status_fulfillment_created_at_id`는 PostgreSQL이 컬럼 삭제 시 자동으로 함께 제거한다 — 별도 `DROP INDEX` 불필요.)

- [ ] **Step 2: domain-order 전체 빌드로 ddl-auto=validate 통과 확인**

Run: `./gradlew :module-domains:domain-order:build`
Expected: PASS — `Order` 엔티티가 더 이상 매핑하지 않는 컬럼이 실제로 사라져도 `ddl-auto=validate`는 엔티티가 기대하는 컬럼만 검증하므로 그대로 통과한다.

- [ ] **Step 3: Commit**

```bash
git add module-domains/domain-order/src/main/resources/db/migration/ordering/V5__drop_fulfillment_columns_from_orders.sql
git commit -m "feat: orders 이행축 컬럼 제거 (contract)"
```

---

## Task 6: DOMAIN_MODEL.md 갱신

**Files:**
- Modify: `DOMAIN_MODEL.md`

- [ ] **Step 1: "6. 주문 (order)" 절 갱신**

`DOMAIN_MODEL.md`의 549~562행(섹션 헤더~"스키마/테이블" 줄)을 아래로 교체:

```markdown
## 6. 주문 (order)

주문과 주문 라인을 소유한다. 라인은 주문 시점의 변형·상품명·옵션·단가를, 배송지는 주문 시점 값을 스냅샷으로 보관한다(상품·변형·회원 변경과 무관하게 내역을 보존). 결제 축(`OrderStatus`)에 더해, 라인 단위 취소·반품 축(`OrderLineStatus`)과 주문 단위 반품 요청 축(`ReturnStatus`)을 갖는다. 이행(배송) 축(`FulfillmentStatus`)은 별도 `Fulfillment` 애그리거트가 소유한다 — 결제·취소·반품축이 금액 불변식(`refundedAmount`·`OrderLine`)을 공유해 분리할 수 없었던 것과 달리, 이행축은 다른 축의 상태를 읽기만 하고 쓰지 않아 독립된 애그리거트로 분리했다(같은 트랜잭션에서 두 애그리거트를 함께 쓰지 않는다).

- 애그리거트 루트: `Order`(결제·취소·부분취소·반품), 자식: `OrderLine`. `Fulfillment`(이행)는 별도 애그리거트 루트로, `orderId` ID 논리참조로만 연결된다.
- 스키마/테이블: `ordering` / `orders`, `order_line`, `fulfillment` (예약어 회피 divergence — 용어집 참조)
```

`Order 필드` 표(556~591행)에서 `fulfillmentStatus`·`shippedAt`·`carrier`·`trackingNumber`·`deliveredAt`·`holdReason` 6개 행을 삭제한다. `cancelRequestedAt`·`cancelledAt`·`cancellationReason`·`returnStatus`·`returnRequestedAt`·`returnReason` 행은 남기되 비고에 "`Cancellation`/`ReturnRequest` 임베더블 컴포넌트"라고 덧붙인다.

`### OrderLine 필드` 절(602행) 앞에 신규 절을 삽입:

```markdown
### Fulfillment 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| orderId | UUID | 필수 | 대상 주문 참조(ID 논리참조, 유니크). 결제 완료 시 `OrderPaid` 소비로 생성 |
| status | FulfillmentStatus | 필수 | 아래 이행 상태표. 최초 PREPARING(결제 전 주문은 행 자체가 없다 — 조회 시 NOT_STARTED로 합성) |
| shippedAt | Instant | 선택 | 출고 시각(SHIPPED 전 null) |
| carrier | String | 선택 | 택배사(`ship` 세팅, SHIPPED 전 null) |
| trackingNumber | String | 선택 | 운송장 번호(`ship` 세팅, SHIPPED 전 null) |
| deliveredAt | Instant | 선택 | 배송 완료 시각(DELIVERED 전 null) |
| holdReason | HoldReason | 선택 | 이행 보류 사유. ON_HOLD에서만 존재 |
| version | long | 필수 | 낙관락 버전 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태 (FulfillmentStatus)

| 상태 | 의미 |
|---|---|
| NOT_STARTED | 이행 미개시. 결제 전 주문의 조회 시 합성값(영속 상태로는 갖지 않는다) |
| PREPARING | 출고 준비. `OrderPaid` 소비로 `Fulfillment` 생성 시 진입 |
| ON_HOLD | 이행 보류. PREPARING에서만 진입 |
| SHIPPED | 출고 완료. PREPARING에서 `ship`으로 진입 |
| DELIVERED | 배송 완료. SHIPPED에서만 진입 |

- 전이: `PREPARING → SHIPPED`(`ship`, 주문 취소 진행 중이면 거부), `PREPARING → ON_HOLD`(`hold`) `→ PREPARING`(`release`), `SHIPPED → DELIVERED`(`confirmDelivery`).
- 생성: `Order.markPaid`가 발행한 `OrderPaid`를 `OrderPaidListener`가 소비해 `Fulfillment.create(orderId)`로 만든다(별도 트랜잭션 — 결제 완료와 이행 생성은 서로 다른 애그리거트라 하나의 트랜잭션에 담지 않는다). `order_id` 유니크 인덱스로 재전달을 멱등 처리한다.
```

- [ ] **Step 2: Commit**

```bash
git add DOMAIN_MODEL.md
git commit -m "docs: DOMAIN_MODEL Order/Fulfillment 애그리거트 분해 반영"
```

---

## Task 7: 전체 빌드 최종 확인

**Files:** 없음(검증만).

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew build`
Expected: PASS — 전 모듈(도메인·쿼리·앱·`module-tests/test-architecture` 포함) 컴파일·Spotless·NullAway·Error Prone·전체 테스트·아키텍처 테스트가 통과한다. 아키텍처 테스트는 신규 `Fulfillment` 애그리거트에도 기존 불변식(리포지토리는 애그리거트 루트당 1개, 연관 `fetch=LAZY`·`@ForeignKey(NO_CONSTRAINT)` 명시, setter 부재, `EnumType.STRING` 등)을 그대로 적용해 검증한다.

실패 시: 실패한 모듈·테스트를 확인해 해당 Task로 돌아가 수정한다. 새 파일을 추가하지 않는다.

- [ ] **Step 2: 최종 상태 확인**

Run: `git status`
Expected: 워킹 트리 clean (Task 1~6에서 전부 커밋 완료).

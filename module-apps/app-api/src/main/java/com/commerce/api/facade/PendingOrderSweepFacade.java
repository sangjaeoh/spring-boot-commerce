package com.commerce.api.facade;

import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.info.OrderLineInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.service.PaymentReader;
import com.commerce.stock.service.StockModifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 요청 이전에 중단돼 payment 행 없이 남은 PENDING 주문을 주기 스윕이 보상 종결하고, 종결 기록된 결제가
 * 남긴 PENDING 잔여를 결제 리컨실({@link PaymentConfirmationFacade})에 위임한다.
 */
@Component
public class PendingOrderSweepFacade {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderSweepFacade.class);

    // 라인 재고 복원의 낙관락 충돌 재시도 상한(첫 시도 포함) — 일시 경합만 흡수한다.
    private static final int RESTORE_MAX_ATTEMPTS = 3;

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final Duration staleAfter;
    private final Clock clock;
    private final Counter processedOrders;
    private final Counter failedOrders;
    private final Counter skippedStockRestores;

    public PendingOrderSweepFacade(
            OrderReader orderReader,
            PaymentReader paymentReader,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            PaymentConfirmationFacade paymentConfirmationFacade,
            @Value("${order.reconciliation.stale-after}") Duration staleAfter,
            @Value("${payment.reconciliation.stale-after}") Duration paymentStaleAfter,
            Clock clock,
            MeterRegistry meterRegistry) {
        // 관할 분리의 전제: order 유예가 payment 유예 이상이어야 REQUESTED 행 있는 PENDING이 결제 리컨실에
        // 먼저 잡힌다. 역전되면 이 스윕이 먼저 손대 "이중 개입 차단"이 조용히 깨지므로 기동 시점에 거부한다.
        if (staleAfter.compareTo(paymentStaleAfter) < 0) {
            throw new IllegalArgumentException("order.reconciliation.stale-after는 payment.reconciliation.stale-after"
                    + " 이상이어야 한다: order=" + staleAfter + " payment=" + paymentStaleAfter);
        }
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.paymentConfirmationFacade = paymentConfirmationFacade;
        this.staleAfter = staleAfter;
        this.clock = clock;
        // 조용한 잔존·보상 실패를 수치로 관측한다 — 처리·실패 건수와 증거 없는 복원 생략(운영 대사 대상).
        this.processedOrders = meterRegistry.counter("sweep.pending_orders.processed");
        this.failedOrders = meterRegistry.counter("sweep.pending_orders.failed");
        this.skippedStockRestores = meterRegistry.counter("compensation.stock_restore.skipped");
    }

    /** 유예가 지난 PENDING 주문을 주기적으로 스윕한다. 노드 간 동시 스윕은 분산 락이 하나로 줄인다. */
    @Scheduled(fixedDelayString = "${order.reconciliation.fixed-delay}")
    // lockAtMostFor는 스윕 소요 상한이다 — PG 조회 없이 DB 보상 트랜잭션만이라 결제 리컨실보다 빠르지만
    // 같은 10분 상한을 둔다. 크래시로 남은 락은 10분 뒤 회수되고, 상한 초과로 실행이 겹쳐도 보상이 주문
    // 취소의 1회성 전이 뒤라 무해하다. 락 획득 실패는 정상 건너뜀이고 다음 주기가 재시도한다.
    // 락의 목적·기각 대안은 REQUIREMENTS.md 제약·전제가 소유한다.
    @SchedulerLock(name = "pending-order-sweep", lockAtMostFor = "PT10M")
    public void reconcileStalePending() {
        reconcile(clock.instant().minus(staleAfter));
    }

    /** 기준 시각 이전에 생성된 PENDING 주문을 전부 스윕한다. 반복 실행에 멱등하다. */
    public void reconcile(Instant cutoff) {
        for (OrderInfo order : orderReader.findPendingBefore(cutoff)) {
            try {
                sweep(order);
                processedOrders.increment();
            } catch (RuntimeException e) {
                // 한 주문의 종결 실패가 스윕을 중단시키지 않는다 — 남은 대상을 계속 처리하고 다음 스윕이 재시도한다.
                failedOrders.increment();
                log.warn("PENDING 주문 스윕 종결 실패: orderId={}", order.id(), e);
            }
        }
    }

    /**
     * payment 행이 없는 주문만 직접 보상 종결하고, 있으면 결제 리컨실 관할로 넘긴다.
     *
     * <p>취소를 복원 앞에 두어 반복 스윕에도 복원이 정확히 한 번이다.
     */
    private void sweep(OrderInfo order) {
        // 1. 관할 판정
        if (paymentReader.hasPaymentForOrder(order.id())) {
            delegateToPaymentReconciliation(order);
            return;
        }
        log.warn(
                "payment 행 없는 유예 경과 PENDING 주문을 스윕이 보상 종결한다: orderId={} orderNumber={}",
                order.id(),
                order.orderNumber());
        // 2. 주문 취소
        orderModifier.cancel(order.id(), CancellationReason.CHECKOUT_ABANDONED);
        // 3. 쿠폰 복원
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, order.id());
        }
        // 4. 재고 복원
        restoreDeductedStock(order);
    }

    /**
     * 차감 완료 증거(주문 단위 차감 마커)가 있는 주문만 전 라인 재고를 복원한다. 증거가 없으면 차감 전·차감 중
     * 중단 잔여라 실제 차감량을 알 수 없으므로 복원을 생략한다 — 과복원(재고 증식→오버셀) 대신 과소복원(팬텀
     * 품절)을 택하고 운영 대사로 강등한다.
     */
    private void restoreDeductedStock(OrderInfo order) {
        if (order.stockDeductedAt() == null) {
            skippedStockRestores.increment();
            log.warn(
                    "차감 완료 증거 없는 PENDING 잔여라 재고 복원을 생략한다(운영 대사 대상): orderId={} orderNumber={}",
                    order.id(),
                    order.orderNumber());
            return;
        }
        for (OrderLineInfo line : order.lines()) {
            restoreLineWithRetry(line.variantId(), line.quantity());
        }
    }

    /**
     * 라인 재고를 복원한다. 동시 체크아웃과의 낙관락 충돌은 일시 경합이라 라인 단위로 짧게 재시도한다 —
     * {@code restore}는 가산·교환법칙이고 충돌한 시도는 자기 트랜잭션째 롤백되므로 재시도가 이중 가산하지
     * 않는다. 재시도 소진 시 전파해 주문 단위 격리(경고 로그 후 다음 주문)로 넘긴다.
     */
    private void restoreLineWithRetry(UUID variantId, int quantity) {
        for (int attempt = 1; ; attempt++) {
            try {
                stockModifier.restore(variantId, quantity);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= RESTORE_MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    /** 결제 상태로 관할을 가른다. REQUESTED는 결제 리컨실 스윕에 맡기고, 종결 기록된 결제만 확정 경로로 넘긴다. */
    private void delegateToPaymentReconciliation(OrderInfo order) {
        PaymentInfo payment = paymentReader.getByOrderId(order.id());
        if (payment.status() == PaymentStatus.REQUESTED) {
            // 미확정 결제 리컨실 스윕이 PG 상태 조회로 확정한다 — 이중 개입하지 않는다.
            return;
        }
        log.warn(
                "종결 기록된 결제가 남긴 유예 경과 PENDING 주문을 결제 리컨실에 위임한다: orderId={} orderNumber={}"
                        + " paymentId={} paymentStatus={}",
                order.id(),
                order.orderNumber(),
                payment.id(),
                payment.status());
        paymentConfirmationFacade.confirm(payment.id());
    }
}

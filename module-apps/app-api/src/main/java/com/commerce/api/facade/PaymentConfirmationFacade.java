package com.commerce.api.facade;

import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.info.OrderLineInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.port.GatewayTransactionStatus;
import com.commerce.payment.service.PaymentProcessor;
import com.commerce.payment.service.PaymentReader;
import com.commerce.stock.service.StockModifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 응답 유실로 미확정(REQUESTED)에 머문 결제를 PG 거래 상태 조회로 확정하고, 종결 기록된(비REQUESTED) 결제가
 * 남긴 주문측 잔여를 마저 종결한다 — 주기 리컨실과 웹훅 통지가 공용하는 확정 경로다. 동기 체크아웃이 정상
 * 종결한 결제는 여기 도달하지 않는다.
 *
 * <p>웹훅 페이로드의 결과를 신뢰하지 않는다. 통지는 대상 결제를 지목하는 트리거일 뿐 확정 근거는 항상 PG
 * 상태 조회라 페이로드 위조가 상태를 왜곡하지 못한다. 이미 종결된 결제·주문 쌍은 조용히 통과하므로 중복
 * 전달·반복 스윕에 멱등하다. 생성 후 유예({@code payment.reconciliation.stale-after})가 지나지 않은 결제도
 * 손대지 않는다 — 동기 체크아웃이 아직 소유 중일 수 있어, 이 유예가 확정 경로와 동기 경로의 경합을 차단한다.
 *
 * <p>주기 스윕은 REQUESTED 결제만 선별한다. 종결 기록된 결제 × 미종결 주문 잔여(승인 커밋 후 결제완료 전
 * 중단, 거절 기록 후 보상 취소 실패)는 결제 상태만으로는 정상 종결과 구분할 수 없어 PENDING 주문 스윕
 * ({@link PendingOrderSweepFacade})이 발견해 위임하고, 웹훅 재전달도 같은 종결 경로에 닿는다.
 *
 * <p>결제 상태 기록을 주문측 효과(결제완료·보상) 뒤 마지막에 둔다. 중간에 중단돼도 결제가 REQUESTED로 남아
 * 다음 스윕이 같은 분기를 재실행하고, 주문측 효과는 상태 가드로 재실행을 건너뛴다. 고아 청구 환불도 PG
 * 환불을 선행하고 승인·취소 기록을 한 커밋으로 남겨 같은 자기복구를 유지한다. 보상의 재고·쿠폰 복원은
 * 주문 취소의 1회성 전이 뒤에만 태워 반복 실행이 이중 복원하지 않고, 재고 복원은 체크아웃의 차감 완료
 * 마커가 추가로 게이트한다 — 증거 없으면 복원을 생략해 과복원(재고 증식)을 차단한다. 라인 복원의 일시
 * 낙관락 충돌은 라인 단위 재시도가 흡수하므로, 취소 커밋 후 복원 유실의 잔여 트리거는 중단(크래시)과
 * 재시도 소진이다(DOMAIN_MODEL.md 미확정 결제 리컨실 절 참조).
 */
@Component
public class PaymentConfirmationFacade {

    // 스윕 분산 락 이름. 락 검증 테스트가 같은 락을 참조하므로 상수로 공유한다.
    static final String LOCK_NAME = "payment-reconciliation";

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationFacade.class);

    /** 라인 재고 복원의 낙관락 충돌 재시도 상한(첫 시도 포함) — 일시 경합만 흡수한다. */
    private static final int RESTORE_MAX_ATTEMPTS = 3;

    private final PaymentReader paymentReader;
    private final PaymentProcessor paymentProcessor;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final Duration staleAfter;
    private final Clock clock;
    private final Counter processedPayments;
    private final Counter failedPayments;
    private final Counter skippedStockRestores;

    public PaymentConfirmationFacade(
            PaymentReader paymentReader,
            PaymentProcessor paymentProcessor,
            OrderReader orderReader,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            @Value("${payment.reconciliation.stale-after}") Duration staleAfter,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.paymentReader = paymentReader;
        this.paymentProcessor = paymentProcessor;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.staleAfter = staleAfter;
        this.clock = clock;
        // 조용한 잔존·보상 실패를 수치로 관측한다 — 확정 처리·실패 건수와 증거 없는 복원 생략(운영 대사 대상).
        this.processedPayments = meterRegistry.counter("reconcile.payments.processed");
        this.failedPayments = meterRegistry.counter("reconcile.payments.failed");
        this.skippedStockRestores = meterRegistry.counter("compensation.stock_restore.skipped");
    }

    /** 유예가 지난 REQUESTED 결제를 주기적으로 리컨실한다. 노드 간 동시 스윕은 분산 락이 하나로 줄인다. */
    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay}")
    // lockAtMostFor는 스윕 소요 상한이다 — 평시 대상 0~수 건 × (PG 조회 + 확정 트랜잭션)으로 초 단위지만
    // 장애 복구 직후 적체를 감안해 10분을 둔다. 크래시로 남은 락은 10분 뒤 회수되고, 상한 초과로 락이 풀려
    // 실행이 겹쳐도 확정이 멱등이라 무해하다. 락 획득 실패는 정상 건너뜀이고 다음 주기가 재시도한다.
    // 락의 목적·기각 대안은 REQUIREMENTS.md 제약·전제가 소유한다.
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M")
    public void reconcileStaleRequested() {
        reconcile(clock.instant().minus(staleAfter));
    }

    /** 기준 시각 이전에 생성된 REQUESTED 결제를 전부 PG 상태 조회로 확정한다. 반복 실행에 멱등하다. */
    public void reconcile(Instant cutoff) {
        for (PaymentInfo payment : paymentReader.findRequestedBefore(cutoff)) {
            try {
                confirm(payment);
                processedPayments.increment();
            } catch (RuntimeException e) {
                // 한 결제의 확정 실패가 스윕을 중단시키지 않는다 — 남은 대상을 계속 처리하고 다음 스윕이 재시도한다.
                failedPayments.increment();
                log.warn("결제 리컨실 확정 실패: paymentId={}", payment.id(), e);
            }
        }
    }

    /**
     * 웹훅이 지목한 결제 하나를 종결한다. 미확정(REQUESTED) 결제는 PG 상태 조회로 확정하고, 종결 기록된
     * 결제는 주문측 잔여를 마저 종결한다. 이미 종결된 결제·주문 쌍과 유예가 지나지 않은 결제는 무시한다
     * (중복 전달 무해·동기 경로 경합 차단).
     *
     * @throws com.commerce.payment.exception.PaymentNotFoundException 결제가 없으면
     */
    public void confirm(UUID paymentId) {
        PaymentInfo payment = paymentReader.getPayment(paymentId);
        if (payment.createdAt().isAfter(clock.instant().minus(staleAfter))) {
            return;
        }
        confirm(payment);
    }

    private void confirm(PaymentInfo payment) {
        if (payment.status() == PaymentStatus.REQUESTED) {
            confirmFromGateway(payment);
            return;
        }
        settleRecorded(payment);
    }

    /** 미확정(REQUESTED) 결제를 PG 거래 상태 조회로 확정한다. */
    private void confirmFromGateway(PaymentInfo payment) {
        GatewayTransactionStatus transaction = paymentProcessor.inquireGateway(payment.id());
        OrderInfo order = orderReader.getOrder(payment.orderId());
        switch (transaction.result()) {
            case APPROVED -> confirmApproval(payment, order, Objects.requireNonNull(transaction.pgTransactionId()));
            case DECLINED -> confirmFailure(payment, order, Objects.requireNonNull(transaction.failureReason()));
            // 청구가 PG에 도달하지 않았다 — 돈이 움직이지 않았으므로 실패로 확정해도 안전하다.
            case NOT_FOUND -> confirmFailure(payment, order, FailureReason.GATEWAY_ERROR);
        }
    }

    private void confirmApproval(PaymentInfo payment, OrderInfo order, String pgTransactionId) {
        switch (order.status()) {
            case PENDING -> {
                orderModifier.markPaid(order.id());
                paymentProcessor.confirmApproval(payment.id(), pgTransactionId);
            }
            // 이전 확정이 결제완료 후 결제 기록 전에 중단된 잔여 — 기록만 마저 한다.
            case PAID -> paymentProcessor.confirmApproval(payment.id(), pgTransactionId);
            // 주문은 이미 취소·환불로 종결됐고 지연 승인된 청구만 고아로 남았다 — 환불을 선행하고 승인·취소를
            // 한 커밋으로 기록한다. 어느 지점에서 중단돼도 REQUESTED로 남아 다음 스윕이 재시도한다.
            case CANCELLED, REFUNDED -> paymentProcessor.confirmOrphanedApproval(payment.id(), pgTransactionId);
        }
    }

    private void confirmFailure(PaymentInfo payment, OrderInfo order, FailureReason failureReason) {
        if (order.status() == OrderStatus.PAID) {
            // 모델상 도달 불가(PAID는 PG 승인 후에만) — PG 응답이 모순이므로 손대지 않고 다음 스윕에 남긴다.
            log.warn("PAID 주문의 결제가 PG에서 승인 아님으로 조회됐다: paymentId={} orderId={}", payment.id(), order.id());
            return;
        }
        if (order.status() == OrderStatus.PENDING) {
            compensatePendingOrder(order);
        }
        paymentProcessor.confirmFailure(payment.id(), failureReason);
    }

    /**
     * 종결 기록된(비REQUESTED) 결제가 남긴 주문측 잔여를 결제 상태 × 주문 상태의 함수로 종결한다. 결제
     * 상태는 이미 기록됐으므로 PG를 조회하지 않는다.
     */
    private void settleRecorded(PaymentInfo payment) {
        OrderInfo order = orderReader.getOrder(payment.orderId());
        if (payment.status() == PaymentStatus.APPROVED) {
            settleApprovedResidue(payment, order);
        } else if (payment.status() == PaymentStatus.FAILED) {
            settleFailedResidue(payment, order);
        }
        // CANCELLED(환불 완결)의 주문측 잔여(PAID 주문 취소 실패)는 취소 파사드의 재시도가 소유한다 — 손대지 않는다.
    }

    private void settleApprovedResidue(PaymentInfo payment, OrderInfo order) {
        switch (order.status()) {
            // 승인 커밋과 결제완료 전이 사이 중단 잔여 — 돈은 이미 빠졌으므로 주문을 결제완료로 완결한다.
            case PENDING -> orderModifier.markPaid(order.id());
            // 정상 종결 쌍 — 중복 전달·재발견 무해.
            case PAID -> {}
            // 주문은 취소·환불로 종결됐는데 청구만 승인으로 남았다 — 고아 청구를 환불한다.
            case CANCELLED, REFUNDED -> paymentProcessor.cancel(payment.id());
        }
    }

    private void settleFailedResidue(PaymentInfo payment, OrderInfo order) {
        if (order.status() == OrderStatus.PAID) {
            // 모델상 도달 불가(PAID는 PG 승인 후에만) — 기록이 모순이므로 손대지 않는다.
            log.warn("PAID 주문의 결제가 실패로 기록돼 있다: paymentId={} orderId={}", payment.id(), order.id());
            return;
        }
        // 거절 기록 후 보상 취소가 실패한 잔여 — 취소 전이 후 복원으로 보상 종결한다.
        if (order.status() == OrderStatus.PENDING) {
            compensatePendingOrder(order);
        }
    }

    private void compensatePendingOrder(OrderInfo order) {
        orderModifier.cancel(order.id(), CancellationReason.PAYMENT_FAILED);
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, order.id());
        }
        // 재고 복원은 차감 완료 마커가 게이트한다. payment 행은 체크아웃이 마커 커밋 뒤에만 만들므로 여기
        // 도달한 주문은 증거가 있는 게 정상이지만, 그 보장은 체크아웃 단계 순서라는 비국소 불변식이라 복원
        // 지점마다 같은 게이트로 지역 강제한다 — 증거 없으면 복원을 생략한다(과복원 대신 팬텀 품절, 운영 대사).
        if (order.stockDeductedAt() == null) {
            skippedStockRestores.increment();
            log.warn(
                    "차감 완료 증거 없는 PENDING 보상이라 재고 복원을 생략한다(운영 대사 대상): orderId={} orderNumber={}",
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
     * 않는다. 재시도 소진 시 전파해 결제 단위 격리(경고 로그 후 다음 결제)로 넘긴다.
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
}

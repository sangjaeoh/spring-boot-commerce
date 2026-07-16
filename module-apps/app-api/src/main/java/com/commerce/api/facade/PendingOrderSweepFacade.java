package com.commerce.api.facade;

import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.info.OrderLineInfo;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.service.PaymentReader;
import com.commerce.stock.service.StockModifier;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 요청 이전에 중단돼 payment 행 없이 남은 PENDING 주문을 주기 스윕이 보상 종결한다.
 *
 * <p>체크아웃은 주문 PENDING을 사가 앵커로 먼저 커밋한 뒤 재고 차감·쿠폰 확정·결제 요청을 잇는다. 주문 생성
 * 이후~결제 요청 이전 구간에서 프로세스가 중단되면 PENDING 주문·차감 재고·사용 쿠폰이 남되 payment 행이
 * 없다 — 미확정 결제 리컨실({@link PaymentConfirmationFacade})은 REQUESTED 결제만 스윕하므로 이 잔여를 영원히
 * 발견하지 못한다(팬텀 품절, 유일한 무한 잔존 경로).
 *
 * <p>관할은 payment 행 존재로 가른다. payment 행이 있으면 미확정 결제 리컨실 관할이라 건드리지 않고(그
 * 리컨실의 PENDING 분기가 취소·복원을 소유한다), 없으면 이 스윕이 직접 보상한다. 생성 후 유예
 * ({@code order.reconciliation.stale-after})가 지난 주문만 손대 진행 중 체크아웃과 경합하지 않는다 — 유예를
 * 결제 리컨실 유예 이상으로 두어, payment 행 있는 PENDING이 결제 리컨실에 먼저 잡히고 유예 지난
 * 무-payment PENDING엔 새 payment 행이 생기지 않는다(payment 행을 쓰는 유일 경로가 그 주문의 체크아웃이다).
 *
 * <p>보상은 취소·리컨실 흐름과 같은 순서다: 주문 CANCELLED 1회성 전이 선행 → 쿠폰 복원(멱등) → 재고 복원
 * (가산). 스윕 쿼리가 PENDING만 반환하므로 반복 실행이 이미 취소된 주문을 재조회하지 않아 복원이 정확히 한
 * 번이다. 취소 전이 커밋과 복원 사이 중단의 복원 유실은 취소·리컨실 흐름과 같은 잔여 한계다(무손실 복구 범위
 * 밖). 처리 건마다 경고 로그를 남겨 잔존 발생을 관측한다.
 */
@Component
public class PendingOrderSweepFacade {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderSweepFacade.class);

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final Duration staleAfter;

    public PendingOrderSweepFacade(
            OrderReader orderReader,
            PaymentReader paymentReader,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            @Value("${order.reconciliation.stale-after}") Duration staleAfter) {
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.staleAfter = staleAfter;
    }

    /** 유예가 지난 PENDING 주문을 주기적으로 스윕한다. 노드 간 동시 스윕은 분산 락이 하나로 줄인다. */
    @Scheduled(fixedDelayString = "${order.reconciliation.fixed-delay}")
    // lockAtMostFor는 스윕 소요 상한이다 — PG 조회 없이 DB 보상 트랜잭션만이라 결제 리컨실보다 빠르지만
    // 같은 10분 상한을 둔다. 크래시로 남은 락은 10분 뒤 회수되고, 상한 초과로 실행이 겹쳐도 보상이 주문
    // 취소의 1회성 전이 뒤라 무해하다. 락 획득 실패는 정상 건너뜀이고 다음 주기가 재시도한다.
    // 락의 목적·기각 대안은 REQUIREMENTS.md 제약·전제가 소유한다.
    @SchedulerLock(name = "pending-order-sweep", lockAtMostFor = "PT10M")
    public void reconcileStalePending() {
        reconcile(Instant.now().minus(staleAfter));
    }

    /** 기준 시각 이전에 생성된 PENDING 주문을 전부 스윕한다. 반복 실행에 멱등하다. */
    public void reconcile(Instant cutoff) {
        for (OrderInfo order : orderReader.findPendingBefore(cutoff)) {
            try {
                sweep(order);
            } catch (RuntimeException e) {
                // 한 주문의 종결 실패가 스윕을 중단시키지 않는다 — 남은 대상을 계속 처리하고 다음 스윕이 재시도한다.
                log.warn("PENDING 주문 스윕 종결 실패: orderId={}", order.id(), e);
            }
        }
    }

    private void sweep(OrderInfo order) {
        if (paymentReader.hasPaymentForOrder(order.id())) {
            // 결제 행이 있으면 미확정 결제 리컨실 관할이라 건드리지 않는다(이중 개입 차단).
            return;
        }
        log.warn(
                "payment 행 없는 유예 경과 PENDING 주문을 스윕이 보상 종결한다: orderId={} orderNumber={}",
                order.id(),
                order.orderNumber());
        orderModifier.cancel(order.id(), CancellationReason.CHECKOUT_ABANDONED);
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId);
        }
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }
}

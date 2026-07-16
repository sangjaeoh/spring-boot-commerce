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
 * 결제 요청 이전에 중단돼 payment 행 없이 남은 PENDING 주문을 주기 스윕이 보상 종결하고, 종결 기록된 결제가
 * 남긴 PENDING 잔여를 결제 리컨실에 위임한다.
 *
 * <p>체크아웃은 주문 PENDING을 사가 앵커로 먼저 커밋한 뒤 재고 차감·쿠폰 확정·결제 요청을 잇는다. 주문 생성
 * 이후~결제 요청 이전 구간에서 프로세스가 중단되면 PENDING 주문·차감 재고·사용 쿠폰이 남되 payment 행이
 * 없다 — 미확정 결제 리컨실({@link PaymentConfirmationFacade})은 REQUESTED 결제만 스윕하므로 이 잔여를 영원히
 * 발견하지 못한다(팬텀 품절, 자동 복구 없던 무한 잔존 경로).
 *
 * <p>관할은 payment 행 존재·상태로 가른다. 행이 없으면 이 스윕이 직접 보상한다. REQUESTED 행이 있으면
 * 미확정 결제 리컨실 관할이라 건드리지 않는다(그 리컨실의 PENDING 분기가 취소·복원을 소유한다). 종결
 * 기록된(APPROVED·FAILED) 행이 있으면 결제측 확정은 끝났는데 주문측 종결이 남은 잔여다 — 결제 스윕은
 * REQUESTED만 선별해 이를 영영 보지 못하므로 이 스윕이 발견해 결제 리컨실 확정 경로에 위임한다(APPROVED ×
 * PENDING → 결제완료 완결, FAILED × PENDING → 보상 종결). 생성 후 유예
 * ({@code order.reconciliation.stale-after})가 지난 주문만 손대 진행 중 체크아웃과 경합하지 않는다 — 유예를
 * 결제 리컨실 유예 이상으로 두어, REQUESTED 행 있는 PENDING이 결제 리컨실에 먼저 잡히고 유예 지난
 * 무-payment PENDING엔 새 payment 행이 생기지 않는다(payment 행을 쓰는 유일 경로가 그 주문의 체크아웃이다).
 *
 * <p>보상은 취소·리컨실 흐름과 같은 순서다: 주문 CANCELLED 1회성 전이 선행 → 쿠폰 복원(멱등) → 재고 복원
 * (가산). 재고 복원은 체크아웃이 전 라인 차감 후 남긴 차감 완료 마커가 게이트한다 — 증거 없는 잔여(차감 전·
 * 차감 중 중단)는 복원을 생략해 차감된 적 없는 라인의 과복원(재고 증식→오버셀)을 차단하고, 부분 차감분은
 * 팬텀 품절로 남아 운영 대사 대상이 된다. 스윕 쿼리가 PENDING만 반환하므로 반복 실행이 이미 취소된 주문을
 * 재조회하지 않아 복원이 정확히 한 번이다. 취소 전이 커밋과 복원 사이 중단의 복원 유실은 취소·리컨실 흐름과
 * 같은 잔여 한계다(무손실 복구 범위 밖). 처리 건마다 경고 로그를 남겨 잔존 발생을 관측한다.
 */
@Component
public class PendingOrderSweepFacade {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderSweepFacade.class);

    private final OrderReader orderReader;
    private final PaymentReader paymentReader;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final PaymentConfirmationFacade paymentConfirmationFacade;
    private final Duration staleAfter;

    public PendingOrderSweepFacade(
            OrderReader orderReader,
            PaymentReader paymentReader,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            PaymentConfirmationFacade paymentConfirmationFacade,
            @Value("${order.reconciliation.stale-after}") Duration staleAfter) {
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.paymentConfirmationFacade = paymentConfirmationFacade;
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
            delegateToPaymentReconciliation(order);
            return;
        }
        log.warn(
                "payment 행 없는 유예 경과 PENDING 주문을 스윕이 보상 종결한다: orderId={} orderNumber={}",
                order.id(),
                order.orderNumber());
        orderModifier.cancel(order.id(), CancellationReason.CHECKOUT_ABANDONED);
        UUID issuedCouponId = order.issuedCouponId();
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, order.id());
        }
        restoreDeductedStock(order);
    }

    /**
     * 차감 완료 증거(주문 단위 차감 마커)가 있는 주문만 전 라인 재고를 복원한다. 증거가 없으면 차감 전·차감 중
     * 중단 잔여라 실제 차감량을 알 수 없으므로 복원을 생략한다 — 과복원(재고 증식→오버셀) 대신 과소복원(팬텀
     * 품절)을 택하고 운영 대사로 강등한다.
     */
    private void restoreDeductedStock(OrderInfo order) {
        if (order.stockDeductedAt() == null) {
            log.warn(
                    "차감 완료 증거 없는 PENDING 잔여라 재고 복원을 생략한다(운영 대사 대상): orderId={} orderNumber={}",
                    order.id(),
                    order.orderNumber());
            return;
        }
        for (OrderLineInfo line : order.lines()) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }

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

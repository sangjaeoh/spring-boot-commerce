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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 응답 유실로 미확정(REQUESTED)에 머문 결제를 PG 거래 상태 조회로 확정한다 — 주기 리컨실과 웹훅 통지가
 * 공용하는 확정 경로다. 동기 체크아웃이 정상 종결한 결제는 여기 도달하지 않는다.
 *
 * <p>웹훅 페이로드의 결과를 신뢰하지 않는다. 통지는 대상 결제를 지목하는 트리거일 뿐 확정 근거는 항상 PG
 * 상태 조회라 페이로드 위조가 상태를 왜곡하지 못한다. 이미 종결된 결제는 조용히 통과하므로 중복 전달·반복
 * 스윕에 멱등하다. 생성 후 유예({@code payment.reconciliation.stale-after})가 지나지 않은 결제도 손대지
 * 않는다 — 동기 체크아웃이 아직 소유 중일 수 있어, 이 유예가 확정 경로와 동기 경로의 경합을 차단한다.
 *
 * <p>결제 상태 기록을 주문측 효과(결제완료·보상) 뒤 마지막에 둔다. 중간에 중단돼도 결제가 REQUESTED로 남아
 * 다음 스윕이 같은 분기를 재실행하고, 주문측 효과는 상태 가드로 재실행을 건너뛴다. 보상의 재고·쿠폰 복원은
 * 주문 취소의 1회성 전이 뒤에만 태워 반복 실행이 이중 복원하지 않는다(취소 커밋과 복원 사이 중단의 복원
 * 유실은 취소 파사드와 같은 잔여 한계다 — DOMAIN_MODEL.md 취소·환불 정책 참조).
 */
@Component
public class PaymentConfirmationFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationFacade.class);

    private final PaymentReader paymentReader;
    private final PaymentProcessor paymentProcessor;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final Duration staleAfter;

    public PaymentConfirmationFacade(
            PaymentReader paymentReader,
            PaymentProcessor paymentProcessor,
            OrderReader orderReader,
            OrderModifier orderModifier,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            @Value("${payment.reconciliation.stale-after}") Duration staleAfter) {
        this.paymentReader = paymentReader;
        this.paymentProcessor = paymentProcessor;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.staleAfter = staleAfter;
    }

    /** 유예가 지난 REQUESTED 결제를 주기적으로 리컨실한다. */
    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay}")
    public void reconcileStaleRequested() {
        reconcile(Instant.now().minus(staleAfter));
    }

    /** 기준 시각 이전에 생성된 REQUESTED 결제를 전부 PG 상태 조회로 확정한다. 반복 실행에 멱등하다. */
    public void reconcile(Instant cutoff) {
        for (PaymentInfo payment : paymentReader.findRequestedBefore(cutoff)) {
            try {
                confirm(payment);
            } catch (RuntimeException e) {
                // 한 결제의 확정 실패가 스윕을 중단시키지 않는다 — 남은 대상을 계속 처리하고 다음 스윕이 재시도한다.
                log.warn("결제 리컨실 확정 실패: paymentId={}", payment.id(), e);
            }
        }
    }

    /**
     * 웹훅이 지목한 결제 하나를 PG 상태 조회로 확정한다. 이미 종결된 결제와 유예가 지나지 않은 결제는
     * 무시한다(중복 전달 무해·동기 경로 경합 차단).
     *
     * @throws com.commerce.payment.exception.PaymentNotFoundException 결제가 없으면
     */
    public void confirm(UUID paymentId) {
        PaymentInfo payment = paymentReader.getPayment(paymentId);
        if (payment.createdAt().isAfter(Instant.now().minus(staleAfter))) {
            return;
        }
        confirm(payment);
    }

    private void confirm(PaymentInfo payment) {
        if (payment.status() != PaymentStatus.REQUESTED) {
            return;
        }
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
            // 주문은 동기 보상으로 이미 취소·복원됐고 지연 승인된 청구만 고아로 남았다 — 승인 기록 후 환불한다.
            case CANCELLED -> {
                paymentProcessor.confirmApproval(payment.id(), pgTransactionId);
                paymentProcessor.cancel(payment.id());
            }
        }
    }

    private void confirmFailure(PaymentInfo payment, OrderInfo order, FailureReason failureReason) {
        if (order.status() == OrderStatus.PAID) {
            // 모델상 도달 불가(PAID는 PG 승인 후에만) — PG 응답이 모순이므로 손대지 않고 다음 스윕에 남긴다.
            log.warn("PAID 주문의 결제가 PG에서 승인 아님으로 조회됐다: paymentId={} orderId={}", payment.id(), order.id());
            return;
        }
        if (order.status() == OrderStatus.PENDING) {
            orderModifier.cancel(order.id(), CancellationReason.PAYMENT_FAILED);
            UUID issuedCouponId = order.issuedCouponId();
            if (issuedCouponId != null) {
                issuedCouponModifier.restoreUse(issuedCouponId);
            }
            for (OrderLineInfo line : order.lines()) {
                stockModifier.restore(line.variantId(), line.quantity());
            }
        }
        paymentProcessor.confirmFailure(payment.id(), failureReason);
    }
}

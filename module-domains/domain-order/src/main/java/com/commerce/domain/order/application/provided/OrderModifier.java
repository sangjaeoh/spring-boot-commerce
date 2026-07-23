package com.commerce.domain.order.application.provided;

import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.order.domain.HoldReason;
import com.commerce.domain.order.domain.RefundReason;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderNotFoundException;
import com.commerce.domain.order.domain.exception.OrderStatusException;
import com.commerce.domain.shared.entity.Money;
import com.commerce.event.order.OrderPaid;
import java.util.UUID;

/** 주문 결제·이행 상태 전이를 담당하는 서비스다. */
public interface OrderModifier {

    /**
     * 결제를 완료하고 {@link OrderPaid}를 발행한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    void markPaid(UUID orderId);

    /**
     * 전 라인 재고 차감 완료 증거를 기록한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 진행 중({@code PENDING})이 아니면
     */
    void markStockDeducted(UUID orderId);

    /**
     * 주문을 취소한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 이미 취소됐거나 출고 이후면
     */
    void cancel(UUID orderId, CancellationReason reason);

    /**
     * 라인 취소를 개시한다. 환불액을 확정해 라인에 기록하고 확정 환불액을 반환한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 취소를 개시할 수 없는 주문·라인 상태면
     */
    Money beginLineCancellation(UUID orderId, UUID lineId);

    /**
     * 취소 진행 중 라인을 취소로 완결한다. 전 라인 취소로 주문이 전체 취소에 수렴했으면 참을 반환한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 라인이 취소 진행 중이 아니면
     */
    boolean completeLineCancellation(UUID orderId, UUID lineId);

    /**
     * 결제 완료 주문의 취소 개시를 기록한다. 마커가 있는 동안 출고가 거부된다. 이미 개시된 주문에는
     * 아무것도 하지 않는다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 완료 주문이 아니거나 출고 이후면
     */
    void requestCancellation(UUID orderId);

    /**
     * 배송 완료 주문을 전체 반품 환불 처리한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 결제 완료·배송 완료 주문이 아니거나 이미 환불됐으면
     */
    void refund(UUID orderId, RefundReason reason);

    /**
     * 회원 본인 주문의 반품을 요청한다. 타인 주문은 미존재로 취급한다.
     *
     * @throws OrderNotFoundException 본인 주문이 없으면
     * @throws OrderStatusException 이미 요청 중이거나, 배송 완료된 결제 주문이 아니면
     */
    void requestReturn(UUID orderId, UUID memberId, RefundReason reason);

    /**
     * 회원 본인 주문의 라인 반품을 요청한다. 타인 주문은 미존재로 취급한다.
     *
     * @throws OrderNotFoundException 본인 주문이 없으면
     * @throws OrderStatusException 배송 완료된 결제 주문이 아니거나, 전체 반품이 진행 중이거나, 라인이 주문됨 상태가 아니면
     */
    void requestLineReturn(UUID orderId, UUID memberId, UUID lineId, RefundReason reason);

    /**
     * 라인 반품 요청을 거절한다. 라인은 주문됨으로 돌아가 재요청할 수 있다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 라인이 반품 요청 상태가 아니면
     */
    void rejectLineReturn(UUID orderId, UUID lineId);

    /**
     * 라인 반품 승인을 개시한다. 환불액을 확정해 라인에 기록하고 확정 환불액을 반환한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 라인이 반품 요청 상태가 아니면
     */
    Money beginLineReturn(UUID orderId, UUID lineId);

    /**
     * 반품 진행 중 라인을 반품으로 완결한다. 전 라인 종결로 주문이 환불에 수렴했으면 참을 반환한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 라인이 반품 진행 중이 아니면
     */
    boolean completeLineReturn(UUID orderId, UUID lineId);

    /**
     * 반품 요청을 거절한다. 주문은 PAID·DELIVERED로 남는다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws OrderStatusException 반품 요청 상태가 아니면
     */
    void rejectReturn(UUID orderId);

    /**
     * 출고한다. 택배사·운송장 번호를 기록한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나, 준비 중이 아니거나, 취소가 진행 중이면
     */
    void ship(UUID orderId, String carrier, String trackingNumber);

    /**
     * 배송 완료 처리한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 출고된 상태가 아니면
     */
    void confirmDelivery(UUID orderId);

    /**
     * 이행을 보류한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 준비 중이 아니면
     */
    void holdFulfillment(UUID orderId, HoldReason reason);

    /**
     * 이행 보류를 해제한다.
     *
     * @throws OrderNotFoundException 주문이 없으면
     * @throws FulfillmentStatusException 결제 완료 주문이 아니거나 보류 중이 아니면
     */
    void releaseFulfillment(UUID orderId);
}

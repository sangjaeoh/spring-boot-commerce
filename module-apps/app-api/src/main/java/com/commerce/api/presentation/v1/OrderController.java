package com.commerce.api.presentation.v1;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.OrderCancellationFacade;
import com.commerce.api.presentation.v1.request.CheckoutRequest;
import com.commerce.api.presentation.v1.request.FulfillmentHoldRequest;
import com.commerce.api.presentation.v1.response.CheckoutResponse;
import com.commerce.api.presentation.v1.response.OrderResponse;
import com.commerce.api.presentation.v1.response.PaymentResponse;
import com.commerce.core.money.Money;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.service.PaymentReader;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 체크아웃·취소·이행 전이·조회 엔드포인트다.
 *
 * <p>크로스 도메인 쓰기(체크아웃·취소)는 파사드에, 단일 도메인 쓰기인 이행 전이는 주문 도메인 Modifier에 얇게
 * 위임하고, 조회는 주문·결제 각 도메인 Reader에 위임해 결과를 응답 DTO로 변환한다. 정책 거부·전이 위반·미존재는
 * 도메인/파사드가 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다. 인증이 범위 밖이라 조회의 회원
 * 소유권은 검사하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderCancellationFacade orderCancellationFacade;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final PaymentReader paymentReader;

    public OrderController(
            CheckoutFacade checkoutFacade,
            OrderCancellationFacade orderCancellationFacade,
            OrderModifier orderModifier,
            OrderReader orderReader,
            PaymentReader paymentReader) {
        this.checkoutFacade = checkoutFacade;
        this.orderCancellationFacade = orderCancellationFacade;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
    }

    /** 장바구니를 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        UUID orderId = checkoutFacade.checkout(
                request.memberId(),
                request.shippingAddress().toAddress(),
                Money.of(request.shippingFee()),
                request.issuedCouponId(),
                request.method());
        return CheckoutResponse.from(orderId);
    }

    /** 결제 완료 주문을 취소하고 환불·재고·쿠폰을 복원한다. */
    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID orderId) {
        orderCancellationFacade.cancel(orderId);
    }

    /** 결제 완료 주문을 출고 처리한다. */
    @PostMapping("/{orderId}/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@PathVariable UUID orderId) {
        orderModifier.ship(orderId);
    }

    /** 출고된 주문을 배송 완료 처리한다. */
    @PostMapping("/{orderId}/delivery-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelivery(@PathVariable UUID orderId) {
        orderModifier.confirmDelivery(orderId);
    }

    /** 준비 중인 주문의 이행을 보류한다. */
    @PostMapping("/{orderId}/fulfillment-hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void holdFulfillment(@PathVariable UUID orderId, @Valid @RequestBody FulfillmentHoldRequest request) {
        orderModifier.holdFulfillment(orderId, request.reason());
    }

    /** 보류된 주문의 이행을 준비 중으로 되돌린다. */
    @PostMapping("/{orderId}/fulfillment-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseFulfillment(@PathVariable UUID orderId) {
        orderModifier.releaseFulfillment(orderId);
    }

    /** 주문 상세를 조회한다. */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return OrderResponse.from(orderReader.getOrder(orderId));
    }

    /** 회원의 주문 목록을 최신순으로 조회한다. */
    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam UUID memberId) {
        return orderReader.getOrdersByMember(memberId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** 주문의 결제 정보(승인·환불 거래)를 조회한다. */
    @GetMapping("/{orderId}/payment")
    public PaymentResponse getPayment(@PathVariable UUID orderId) {
        return PaymentResponse.from(paymentReader.getByOrderId(orderId));
    }
}

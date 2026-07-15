package com.commerce.api.presentation.v1;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.OrderCancellationFacade;
import com.commerce.api.facade.OrderRefundFacade;
import com.commerce.api.presentation.v1.request.CheckoutRequest;
import com.commerce.api.presentation.v1.request.FulfillmentHoldRequest;
import com.commerce.api.presentation.v1.request.OrderRefundRequest;
import com.commerce.api.presentation.v1.response.CheckoutResponse;
import com.commerce.api.presentation.v1.response.OrderPageResponse;
import com.commerce.api.presentation.v1.response.OrderResponse;
import com.commerce.api.presentation.v1.response.PaymentResponse;
import com.commerce.core.money.Money;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.service.PaymentReader;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
 * <p>본인용 표면(체크아웃·취소·조회)은 회원을 토큰 주체({@link AuthUser})에서 도출하고 미인증 요청은
 * 401로 거부된다. 타인 주문은 존재 누출 방지로 미존재(404) 취급한다(발급 쿠폰 관례와 동일). 이행 전이·반품
 * 환불은 관리자 표면이라 관리자 토큰만 허용한다({@link AdminOnly}). 크로스 도메인 쓰기(체크아웃·취소·반품
 * 환불)는 파사드에, 단일 도메인 쓰기인 이행 전이는 주문 도메인 Modifier에 얇게 위임하고, 조회는 주문·결제 각 도메인 Reader에
 * 위임해 결과를 응답 DTO로 변환한다. 정책 거부·전이 위반·미존재는 도메인/파사드가 던지는 예외를 전역
 * 핸들러가 problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderCancellationFacade orderCancellationFacade;
    private final OrderRefundFacade orderRefundFacade;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final PaymentReader paymentReader;

    public OrderController(
            CheckoutFacade checkoutFacade,
            OrderCancellationFacade orderCancellationFacade,
            OrderRefundFacade orderRefundFacade,
            OrderModifier orderModifier,
            OrderReader orderReader,
            PaymentReader paymentReader) {
        this.checkoutFacade = checkoutFacade;
        this.orderCancellationFacade = orderCancellationFacade;
        this.orderRefundFacade = orderRefundFacade;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.paymentReader = paymentReader;
    }

    /** 본인 장바구니를 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(AuthUser authUser, @Valid @RequestBody CheckoutRequest request) {
        UUID orderId = checkoutFacade.checkout(
                authUser.memberId(),
                request.shippingAddress().toAddress(),
                Money.of(request.shippingFee()),
                request.issuedCouponId(),
                request.method());
        return CheckoutResponse.from(orderId);
    }

    /** 결제 완료된 본인 주문을 취소하고 환불·재고·쿠폰을 복원한다. */
    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(AuthUser authUser, @PathVariable UUID orderId) {
        orderCancellationFacade.cancel(orderId, authUser.memberId());
    }

    /** 배송 완료 주문을 전체 반품 환불하고 재고·쿠폰을 복원한다. */
    @AdminOnly
    @PostMapping("/{orderId}/refund")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(@PathVariable UUID orderId, @Valid @RequestBody OrderRefundRequest request) {
        orderRefundFacade.refund(orderId, request.reason());
    }

    /** 결제 완료 주문을 출고 처리한다. */
    @AdminOnly
    @PostMapping("/{orderId}/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@PathVariable UUID orderId) {
        orderModifier.ship(orderId);
    }

    /** 출고된 주문을 배송 완료 처리한다. */
    @AdminOnly
    @PostMapping("/{orderId}/delivery-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelivery(@PathVariable UUID orderId) {
        orderModifier.confirmDelivery(orderId);
    }

    /** 준비 중인 주문의 이행을 보류한다. */
    @AdminOnly
    @PostMapping("/{orderId}/fulfillment-hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void holdFulfillment(@PathVariable UUID orderId, @Valid @RequestBody FulfillmentHoldRequest request) {
        orderModifier.holdFulfillment(orderId, request.reason());
    }

    /** 보류된 주문의 이행을 준비 중으로 되돌린다. */
    @AdminOnly
    @PostMapping("/{orderId}/fulfillment-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseFulfillment(@PathVariable UUID orderId) {
        orderModifier.releaseFulfillment(orderId);
    }

    /** 본인 주문 상세를 조회한다. */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(AuthUser authUser, @PathVariable UUID orderId) {
        return OrderResponse.from(orderReader.getOrder(orderId, authUser.memberId()));
    }

    /** 본인 주문 목록을 최신순 페이지로 조회한다. */
    @GetMapping
    public OrderPageResponse getOrders(
            AuthUser authUser,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return OrderPageResponse.from(orderReader.getOrdersByMember(authUser.memberId(), PageRequest.of(page, size)));
    }

    /** 본인 주문의 결제 정보(승인·환불 거래)를 조회한다. */
    @GetMapping("/{orderId}/payment")
    public PaymentResponse getPayment(AuthUser authUser, @PathVariable UUID orderId) {
        // 소유권 게이트 — 본인 주문이 아니면 미존재(404)로 끝난다.
        orderReader.getOrder(orderId, authUser.memberId());
        return PaymentResponse.from(paymentReader.getByOrderId(orderId));
    }
}

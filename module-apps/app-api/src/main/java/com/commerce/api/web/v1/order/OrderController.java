package com.commerce.api.web.v1.order;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.OrderCancellationFacade;
import com.commerce.api.facade.OrderPaymentFacade;
import com.commerce.api.facade.OrderRefundFacade;
import com.commerce.api.web.v1.order.request.CheckoutRequest;
import com.commerce.api.web.v1.order.request.FulfillmentHoldRequest;
import com.commerce.api.web.v1.order.request.OrderRefundRequest;
import com.commerce.api.web.v1.order.request.OrderShipRequest;
import com.commerce.api.web.v1.order.response.CheckoutResponse;
import com.commerce.api.web.v1.order.response.OrderPageResponse;
import com.commerce.api.web.v1.order.response.OrderResponse;
import com.commerce.api.web.v1.payment.response.PaymentResponse;
import com.commerce.core.money.Money;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.paging.PaginationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
 * 환불)와 크로스 도메인 조회(결제 조회)는 파사드에, 단일 도메인 쓰기인 이행 전이는 주문 도메인 Modifier에, 단일 도메인 조회는
 * 주문 도메인 Reader에 얇게 위임해 결과를 응답 DTO로 변환한다. 정책 거부·전이 위반·미존재는 도메인/파사드가 던지는 예외를 전역
 * 핸들러가 problem+json으로 매핑한다.
 */
@Tag(name = "주문", description = "체크아웃·취소·이행 전이·조회")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderCancellationFacade orderCancellationFacade;
    private final OrderRefundFacade orderRefundFacade;
    private final OrderPaymentFacade orderPaymentFacade;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;

    public OrderController(
            CheckoutFacade checkoutFacade,
            OrderCancellationFacade orderCancellationFacade,
            OrderRefundFacade orderRefundFacade,
            OrderPaymentFacade orderPaymentFacade,
            OrderModifier orderModifier,
            OrderReader orderReader) {
        this.checkoutFacade = checkoutFacade;
        this.orderCancellationFacade = orderCancellationFacade;
        this.orderRefundFacade = orderRefundFacade;
        this.orderPaymentFacade = orderPaymentFacade;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
    }

    /** 본인 장바구니를 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다. */
    @Operation(summary = "체크아웃", description = "본인 장바구니를 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "주문 생성·결제 완료"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효 또는 결제 금액이 있는데 결제 수단 누락",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "402",
                description = "결제 거절",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "빈 장바구니·주문 불가·재고 부족·쿠폰 적용 불가·자격 없음·동시성 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
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
    @Operation(summary = "주문 취소", description = "결제 완료된 본인 주문을 취소하고 환불·재고·쿠폰을 복원한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "주문 취소·환불·복원 완료"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음 또는 타인 주문",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "취소할 수 없는 주문 상태·동시성 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(AuthUser authUser, @PathVariable UUID orderId) {
        orderCancellationFacade.cancel(orderId, authUser.memberId());
    }

    /** 배송 완료 주문을 전체 반품 환불하고 재고·쿠폰을 복원한다. */
    @Operation(summary = "반품 환불", description = "배송 완료 주문을 전체 반품 환불하고 재고·쿠폰을 복원한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "반품 환불·복원 완료"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "환불할 수 없는 주문 상태(배송 완료 결제 주문만 환불 가능)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{orderId}/refund")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(@PathVariable UUID orderId, @Valid @RequestBody OrderRefundRequest request) {
        orderRefundFacade.refund(orderId, request.reason());
    }

    /** 결제 완료 주문을 택배사·운송장 번호와 함께 출고 처리한다. */
    @Operation(summary = "출고 처리", description = "결제 완료 주문을 택배사·운송장 번호와 함께 출고 처리한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "출고 처리 완료"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "출고할 수 없는 상태(결제 미완료·취소 진행 중·이행 전이 위반)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{orderId}/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@PathVariable UUID orderId, @Valid @RequestBody OrderShipRequest request) {
        orderModifier.ship(orderId, request.carrier(), request.trackingNumber());
    }

    /** 출고된 주문을 배송 완료 처리한다. */
    @Operation(summary = "배송 완료 처리", description = "출고된 주문을 배송 완료 처리한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "배송 완료 처리 완료"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "배송 완료로 전이할 수 없는 이행 상태",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{orderId}/delivery-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelivery(@PathVariable UUID orderId) {
        orderModifier.confirmDelivery(orderId);
    }

    /** 준비 중인 주문의 이행을 보류한다. */
    @Operation(summary = "이행 보류", description = "준비 중인 주문의 이행을 보류한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "이행 보류 완료"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "보류로 전이할 수 없는 이행 상태",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{orderId}/fulfillment-hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void holdFulfillment(@PathVariable UUID orderId, @Valid @RequestBody FulfillmentHoldRequest request) {
        orderModifier.holdFulfillment(orderId, request.reason());
    }

    /** 보류된 주문의 이행을 준비 중으로 되돌린다. */
    @Operation(summary = "이행 보류 해제", description = "보류된 주문의 이행을 준비 중으로 되돌린다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "이행 보류 해제 완료"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "보류 해제로 전이할 수 없는 이행 상태",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{orderId}/fulfillment-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseFulfillment(@PathVariable UUID orderId) {
        orderModifier.releaseFulfillment(orderId);
    }

    /** 본인 주문 상세를 조회한다. */
    @Operation(summary = "주문 상세 조회", description = "본인 주문 상세를 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 상세"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음 또는 타인 주문",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(AuthUser authUser, @PathVariable UUID orderId) {
        return OrderResponse.from(orderReader.getOrder(orderId, authUser.memberId()));
    }

    /** 본인 주문 목록을 최신순 페이지로 조회한다. */
    @Operation(summary = "본인 주문 목록 조회", description = "본인 주문 목록을 최신순 페이지로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 목록 페이지"),
        @ApiResponse(
                responseCode = "400",
                description = "페이지 파라미터 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public OrderPageResponse getOrders(AuthUser authUser, @Valid @ParameterObject PaginationRequest pagination) {
        return OrderPageResponse.from(orderReader.getOrdersByMember(
                authUser.memberId(), PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 결제·이행 축 상태로 주문 목록을 최신순 페이지로 조회한다(출고·환불 대상 발견). */
    @Operation(summary = "상태별 주문 목록 조회", description = "결제·이행 축 상태로 주문 목록을 최신순 페이지로 조회한다(출고·환불 대상 발견).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 목록 페이지"),
        @ApiResponse(
                responseCode = "400",
                description = "상태·페이지 파라미터 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @GetMapping("/admin")
    public OrderPageResponse getOrdersByStatus(
            @RequestParam OrderStatus status,
            @RequestParam FulfillmentStatus fulfillmentStatus,
            @Valid @ParameterObject PaginationRequest pagination) {
        return OrderPageResponse.from(orderReader.getOrdersByStatus(
                status, fulfillmentStatus, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    /** 본인 주문의 결제 정보(승인·환불 거래)를 조회한다. */
    @Operation(summary = "주문 결제 정보 조회", description = "본인 주문의 결제 정보(승인·환불 거래)를 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 정보"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "주문 없음 또는 타인 주문",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/{orderId}/payment")
    public PaymentResponse getPayment(AuthUser authUser, @PathVariable UUID orderId) {
        return PaymentResponse.from(orderPaymentFacade.getPayment(orderId, authUser.memberId()));
    }
}

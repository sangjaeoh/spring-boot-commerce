package com.commerce.api.web.v1.order;

import com.commerce.api.facade.CheckoutFacade;
import com.commerce.api.facade.OrderCancellationFacade;
import com.commerce.api.facade.OrderPaymentFacade;
import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.order.request.CheckoutPreviewRequest;
import com.commerce.api.web.v1.order.request.CheckoutRequest;
import com.commerce.api.web.v1.order.request.DirectOrderRequest;
import com.commerce.api.web.v1.order.response.CheckoutPreviewResponse;
import com.commerce.api.web.v1.order.response.CheckoutResponse;
import com.commerce.api.web.v1.order.response.DirectOrderResponse;
import com.commerce.api.web.v1.order.response.OrderPageResponse;
import com.commerce.api.web.v1.order.response.OrderResponse;
import com.commerce.api.web.v1.payment.response.PaymentResponse;
import com.commerce.order.service.OrderReader;
import com.commerce.shared.entity.Money;
import com.commerce.web.auth.AuthUser;
import com.commerce.web.paging.PaginationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문 체크아웃·바로구매·미리보기·취소·조회 엔드포인트다. */
@Tag(name = "주문", description = "체크아웃·바로구매·미리보기·취소·조회")
@Authenticated
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderCancellationFacade orderCancellationFacade;
    private final OrderPaymentFacade orderPaymentFacade;
    private final OrderReader orderReader;

    public OrderController(
            CheckoutFacade checkoutFacade,
            OrderCancellationFacade orderCancellationFacade,
            OrderPaymentFacade orderPaymentFacade,
            OrderReader orderReader) {
        this.checkoutFacade = checkoutFacade;
        this.orderCancellationFacade = orderCancellationFacade;
        this.orderPaymentFacade = orderPaymentFacade;
        this.orderReader = orderReader;
    }

    @Operation(summary = "체크아웃", description = "본인 장바구니(전체 또는 선택 라인)를 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다.")
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
                description = "빈 장바구니·선택 라인 부재·주문 불가·재고 부족·쿠폰 적용 불가·자격 없음·동시성 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(AuthUser authUser, @Valid @RequestBody CheckoutRequest request) {
        UUID orderId = checkoutFacade.checkout(
                authUser.memberId(),
                request.variantIds(),
                request.shippingAddress().toAddress(),
                Money.of(request.shippingFee()),
                request.issuedCouponId(),
                request.method());
        return CheckoutResponse.from(orderId);
    }

    @Operation(summary = "바로구매", description = "장바구니를 거치지 않고 요청 라인을 주문·결제로 전환하고 결제 완료된 주문 ID를 반환한다. 장바구니는 변경하지 않는다.")
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
                description = "주문 불가·재고 부족·쿠폰 적용 불가·자격 없음·동시성 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public DirectOrderResponse orderDirect(AuthUser authUser, @Valid @RequestBody DirectOrderRequest request) {
        UUID orderId = checkoutFacade.orderDirect(
                authUser.memberId(),
                request.toLines(),
                request.shippingAddress().toAddress(),
                Money.of(request.shippingFee()),
                request.issuedCouponId(),
                request.method());
        return DirectOrderResponse.from(orderId);
    }

    @Operation(summary = "체크아웃 미리보기", description = "체크아웃과 같은 게이트·산식으로 상품 합계·할인·배송비·결제 예정액을 계산한다. 부작용이 없다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 예정 금액"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "빈 장바구니·선택 라인 부재·주문 불가·재고 부족·쿠폰 적용 불가·자격 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/preview")
    public CheckoutPreviewResponse preview(AuthUser authUser, @Valid @RequestBody CheckoutPreviewRequest request) {
        return CheckoutPreviewResponse.from(checkoutFacade.preview(
                authUser.memberId(), request.variantIds(), Money.of(request.shippingFee()), request.issuedCouponId()));
    }

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
    public void cancel(AuthUser authUser, @Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        orderCancellationFacade.cancel(orderId, authUser.memberId());
    }

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
    public OrderResponse getOrder(AuthUser authUser, @Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        return OrderResponse.from(orderReader.getOrder(orderId, authUser.memberId()));
    }

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
    public PaymentResponse getPayment(AuthUser authUser, @Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        return PaymentResponse.from(orderPaymentFacade.getPayment(orderId, authUser.memberId()));
    }
}

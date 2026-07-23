package com.commerce.app.admin.web.v1.admin.order;

import com.commerce.app.admin.facade.OrderRefundFacade;
import com.commerce.app.admin.web.auth.Admin;
import com.commerce.app.admin.web.v1.admin.order.request.FulfillmentHoldRequest;
import com.commerce.app.admin.web.v1.admin.order.request.OrderRefundRequest;
import com.commerce.app.admin.web.v1.admin.order.request.OrderShipRequest;
import com.commerce.app.admin.web.v1.admin.order.response.OrderPageResponse;
import com.commerce.app.admin.web.v1.admin.order.response.OrderSearchPageResponse;
import com.commerce.common.web.paging.PaginationRequest;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.query.order.OrderSearchReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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

/** 주문 이행 전이·반품 환불·상태별 목록·회원 이메일 검색의 관리자 엔드포인트다. */
@Tag(name = "주문 관리", description = "이행 전이·반품 환불·상태별 목록·회원 이메일 검색")
@Admin
@RestController
@RequestMapping("/api/v1/admin/orders")
public class OrderAdminController {

    private final OrderRefundFacade orderRefundFacade;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final OrderSearchReader orderSearchReader;

    public OrderAdminController(
            OrderRefundFacade orderRefundFacade,
            OrderModifier orderModifier,
            OrderReader orderReader,
            OrderSearchReader orderSearchReader) {
        this.orderRefundFacade = orderRefundFacade;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.orderSearchReader = orderSearchReader;
    }

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
    @PostMapping("/{orderId}/refund")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(
            @Parameter(description = "주문 ID") @PathVariable UUID orderId,
            @Valid @RequestBody OrderRefundRequest request) {
        orderRefundFacade.refund(orderId, request.reason());
    }

    @Operation(summary = "반품 요청 승인", description = "반품 요청된 주문의 환불을 완결하고 재고·쿠폰을 복원한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "반품 승인·환불·복원 완료"),
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
                description = "반품 요청 상태의 주문이 아님",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{orderId}/return-approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveReturn(@Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        orderRefundFacade.approveReturn(orderId);
    }

    @Operation(summary = "반품 요청 거절", description = "반품 요청을 거절한다. 주문은 결제 완료·배송 완료로 남는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "반품 거절 완료"),
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
                description = "반품 요청 상태의 주문이 아님",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{orderId}/return-rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectReturn(@Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        orderModifier.rejectReturn(orderId);
    }

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
    @PostMapping("/{orderId}/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(
            @Parameter(description = "주문 ID") @PathVariable UUID orderId,
            @Valid @RequestBody OrderShipRequest request) {
        orderModifier.ship(orderId, request.carrier(), request.trackingNumber());
    }

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
    @PostMapping("/{orderId}/delivery-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelivery(@Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        orderModifier.confirmDelivery(orderId);
    }

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
    @PostMapping("/{orderId}/fulfillment-hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void holdFulfillment(
            @Parameter(description = "주문 ID") @PathVariable UUID orderId,
            @Valid @RequestBody FulfillmentHoldRequest request) {
        orderModifier.holdFulfillment(orderId, request.reason());
    }

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
    @PostMapping("/{orderId}/fulfillment-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseFulfillment(@Parameter(description = "주문 ID") @PathVariable UUID orderId) {
        orderModifier.releaseFulfillment(orderId);
    }

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
    @GetMapping
    public OrderPageResponse getOrdersByStatus(
            @Parameter(description = "결제 축 주문 상태 필터") @RequestParam OrderStatus status,
            @Parameter(description = "이행 축 상태 필터") @RequestParam FulfillmentStatus fulfillmentStatus,
            @Valid @ParameterObject PaginationRequest pagination) {
        return OrderPageResponse.from(orderReader.getOrdersByStatus(
                status, fulfillmentStatus, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }

    @Operation(summary = "회원 이메일 주문 검색", description = "회원 이메일(정확 일치)과 선택적 주문 상태로 주문을 최신순 페이지로 검색한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "검색 결과 페이지"),
        @ApiResponse(
                responseCode = "400",
                description = "이메일 형식·상태·페이지 파라미터 무효",
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
    @GetMapping("/search")
    public OrderSearchPageResponse searchOrders(
            @Parameter(description = "회원 이메일(정확 일치)") @RequestParam String email,
            @Parameter(description = "결제 축 주문 상태 필터 — 없으면 전 상태") @RequestParam(required = false) @Nullable
                    OrderStatus status,
            @Valid @ParameterObject PaginationRequest pagination) {
        return OrderSearchPageResponse.from(orderSearchReader.getMemberOrderPage(
                email, status, PageRequest.of(pagination.zeroBasedPage(), pagination.size())));
    }
}

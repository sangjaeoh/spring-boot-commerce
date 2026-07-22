package com.commerce.admin.web.v1.admin.stock;

import com.commerce.admin.web.auth.Admin;
import com.commerce.admin.web.v1.admin.stock.request.StockIncreaseRequest;
import com.commerce.admin.web.v1.admin.stock.response.StockResponse;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

/** 재고 운영(현황 조회·재입고·수동 품절/재개·단종) 엔드포인트다. */
@Tag(name = "재고 관리", description = "재고 현황 조회·재입고·상태 전이")
@Admin
@RestController
@RequestMapping("/api/v1/admin/stocks")
public class StockAdminController {

    private final StockModifier stockModifier;
    private final StockReader stockReader;

    public StockAdminController(StockModifier stockModifier, StockReader stockReader) {
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
    }

    @Operation(summary = "재고 현황 조회", description = "변형 ID들의 재고 현황을 조회한다. 재고 행이 없는 변형은 결과에 없다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
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
    })
    @GetMapping
    public List<StockResponse> getStocks(@Parameter(description = "조회할 변형 ID 목록") @RequestParam List<UUID> variantIds) {
        return stockReader.getByVariantIds(variantIds).stream()
                .map(StockResponse::from)
                .toList();
    }

    @Operation(summary = "재고 재입고", description = "변형의 재고를 재입고한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "재입고됨"),
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
                description = "재고 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "단종 재고 재입고 불가 또는 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/increase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void increase(
            @Parameter(description = "변형 ID") @PathVariable UUID variantId,
            @Valid @RequestBody StockIncreaseRequest request) {
        stockModifier.increase(variantId, request.quantity());
    }

    @Operation(summary = "수동 품절", description = "변형의 재고를 수동 품절로 둔다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "품절 처리됨"),
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
                description = "재고 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이 또는 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/mark-sold-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSoldOut(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        stockModifier.markSoldOut(variantId);
    }

    @Operation(summary = "판매 재개", description = "변형의 재고를 판매 가능으로 되돌린다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "판매 재개됨"),
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
                description = "재고 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이 또는 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/mark-sellable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSellable(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        stockModifier.markSellable(variantId);
    }

    @Operation(summary = "재고 단종", description = "변형의 재고를 단종한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "단종됨"),
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
                description = "재고 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이 또는 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{variantId}/discontinue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discontinue(@Parameter(description = "변형 ID") @PathVariable UUID variantId) {
        stockModifier.discontinue(variantId);
    }
}

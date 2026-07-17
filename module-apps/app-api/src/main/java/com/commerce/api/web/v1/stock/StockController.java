package com.commerce.api.web.v1.stock;

import com.commerce.api.web.v1.stock.request.StockIncreaseRequest;
import com.commerce.api.web.v1.stock.response.StockResponse;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import com.commerce.web.auth.AdminOnly;
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
 * 재고 운영(현황 조회·재입고·수동 품절/재개·단종) 엔드포인트다.
 *
 * <p>전부 관리자 표면이라 관리자 토큰만 허용한다({@link AdminOnly}). 재고는 변형당 1행이라 variantId가
 * 자연 키다. 조회는 재고 도메인 Reader에, 단일 도메인 쓰기는 파사드 없이 재고 도메인 Modifier에 얇게 위임하고,
 * 미존재·허용되지 않은 전이·단종 재입고 거부는 도메인이 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다.
 */
@AdminOnly
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockModifier stockModifier;
    private final StockReader stockReader;

    public StockController(StockModifier stockModifier, StockReader stockReader) {
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
    }

    /** 변형 ID들의 재고 현황을 조회한다. 재고 행이 없는 변형은 결과에 없다. */
    @GetMapping
    public List<StockResponse> getStocks(@RequestParam List<UUID> variantIds) {
        return stockReader.getByVariantIds(variantIds).stream()
                .map(StockResponse::from)
                .toList();
    }

    /** 변형의 재고를 재입고한다. */
    @PostMapping("/{variantId}/increase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void increase(@PathVariable UUID variantId, @Valid @RequestBody StockIncreaseRequest request) {
        stockModifier.increase(variantId, request.quantity());
    }

    /** 변형의 재고를 수동 품절로 둔다. */
    @PostMapping("/{variantId}/mark-sold-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSoldOut(@PathVariable UUID variantId) {
        stockModifier.markSoldOut(variantId);
    }

    /** 변형의 재고를 판매 가능으로 되돌린다. */
    @PostMapping("/{variantId}/mark-sellable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSellable(@PathVariable UUID variantId) {
        stockModifier.markSellable(variantId);
    }

    /** 변형의 재고를 단종한다. */
    @PostMapping("/{variantId}/discontinue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discontinue(@PathVariable UUID variantId) {
        stockModifier.discontinue(variantId);
    }
}

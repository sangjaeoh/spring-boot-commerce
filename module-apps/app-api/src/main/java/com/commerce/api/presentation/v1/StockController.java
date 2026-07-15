package com.commerce.api.presentation.v1;

import com.commerce.api.presentation.v1.request.StockIncreaseRequest;
import com.commerce.stock.service.StockModifier;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 운영(재입고·수동 품절/재개·단종) 엔드포인트다.
 *
 * <p>재고는 변형당 1행이라 variantId가 자연 키다. 단일 도메인 쓰기라 파사드 없이 재고 도메인 Modifier에
 * 얇게 위임하고, 미존재·허용되지 않은 전이·단종 재입고 거부는 도메인이 던지는 예외를 전역 핸들러가
 * problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockModifier stockModifier;

    public StockController(StockModifier stockModifier) {
        this.stockModifier = stockModifier;
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

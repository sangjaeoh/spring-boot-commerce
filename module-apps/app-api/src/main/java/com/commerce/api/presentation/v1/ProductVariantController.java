package com.commerce.api.presentation.v1;

import com.commerce.api.presentation.v1.request.VariantPriceChangeRequest;
import com.commerce.core.money.Money;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.web.auth.AdminOnly;
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
 * 상품 변형 관리(가격 변경·활성/비활성/은퇴) 엔드포인트다.
 *
 * <p>전부 관리자 표면이라 관리자 토큰만 허용한다({@link AdminOnly}). 단일 도메인 쓰기라 파사드 없이 상품
 * 도메인 Modifier에 얇게 위임하고, 미존재·허용되지 않은 전이·RETIRED 변경 거부는 도메인이 던지는 예외를
 * 전역 핸들러가 problem+json으로 매핑한다. 가격 변경은 기존 주문 스냅샷에 영향을 주지 않는다.
 */
@AdminOnly
@RestController
@RequestMapping("/api/v1/product-variants")
public class ProductVariantController {

    private final ProductVariantModifier variantModifier;

    public ProductVariantController(ProductVariantModifier variantModifier) {
        this.variantModifier = variantModifier;
    }

    /** 변형 판매가를 바꾼다. */
    @PostMapping("/{variantId}/price-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePrice(@PathVariable UUID variantId, @Valid @RequestBody VariantPriceChangeRequest request) {
        variantModifier.changePrice(variantId, Money.of(request.price()));
    }

    /** 변형을 판매 제공한다. */
    @PostMapping("/{variantId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable UUID variantId) {
        variantModifier.enable(variantId);
    }

    /** 변형 판매 제공을 중단한다. */
    @PostMapping("/{variantId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID variantId) {
        variantModifier.disable(variantId);
    }

    /** 변형을 은퇴시킨다. */
    @PostMapping("/{variantId}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@PathVariable UUID variantId) {
        variantModifier.retire(variantId);
    }
}

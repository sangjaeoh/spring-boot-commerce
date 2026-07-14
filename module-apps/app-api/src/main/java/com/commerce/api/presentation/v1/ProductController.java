package com.commerce.api.presentation.v1;

import com.commerce.api.facade.ProductRegistrationFacade;
import com.commerce.api.presentation.v1.request.ProductRegistrationRequest;
import com.commerce.api.presentation.v1.response.ProductRegistrationResponse;
import com.commerce.core.money.Money;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 등록 엔드포인트다.
 *
 * <p>상품 등록 파사드에 얇게 위임한다. 파사드가 상품·첫 변형·초기 재고를 순차 시딩하고 판매를
 * 시작하며, 컨트롤러는 요청 DTO를 도메인 입력으로, 상품 ID를 응답 DTO로 변환만 한다.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRegistrationFacade productRegistrationFacade;

    public ProductController(ProductRegistrationFacade productRegistrationFacade) {
        this.productRegistrationFacade = productRegistrationFacade;
    }

    /** 상품·첫 변형·초기 재고를 시딩하고 등록된 상품 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRegistrationResponse register(@Valid @RequestBody ProductRegistrationRequest request) {
        UUID productId = productRegistrationFacade.registerProduct(
                request.name(),
                request.description(),
                Money.of(request.price()),
                request.toProductOptions(),
                request.initialQuantity());
        return ProductRegistrationResponse.from(productId);
    }
}

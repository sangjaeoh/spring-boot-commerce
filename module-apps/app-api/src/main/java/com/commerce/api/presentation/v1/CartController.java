package com.commerce.api.presentation.v1;

import com.commerce.api.facade.CartViewFacade;
import com.commerce.api.presentation.v1.response.CartResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 조회 엔드포인트다.
 *
 * <p>장바구니 뷰 파사드에 얇게 위임한다. 파사드가 라인별 변형 현재가·소계·총액을 합성하고, 컨트롤러는 결과를
 * 응답 DTO로 변환만 한다. 인증이 범위 밖이라 회원 소유권은 검사하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartViewFacade cartViewFacade;

    public CartController(CartViewFacade cartViewFacade) {
        this.cartViewFacade = cartViewFacade;
    }

    /** 회원의 장바구니를 변형 현재가·소계·총액과 함께 조회한다. */
    @GetMapping
    public CartResponse getCart(@RequestParam UUID memberId) {
        return CartResponse.from(cartViewFacade.getCartView(memberId));
    }
}

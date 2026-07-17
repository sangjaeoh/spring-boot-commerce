package com.commerce.api.web.v1.cart;

import com.commerce.api.facade.CartCommandFacade;
import com.commerce.api.facade.CartViewFacade;
import com.commerce.api.web.v1.cart.request.AddCartItemRequest;
import com.commerce.api.web.v1.cart.request.ChangeCartItemQuantityRequest;
import com.commerce.api.web.v1.cart.response.CartResponse;
import com.commerce.web.auth.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 조회·쓰기 엔드포인트다.
 *
 * <p>본인용 표면이라 회원은 토큰 주체({@link AuthUser})에서 도출하고 미인증 요청은 401로 거부된다.
 * 조회는 뷰 파사드가 라인별 변형 현재가·소계·총액을 합성하고, 쓰기는 커맨드 파사드가 담기·증량에 주문 자격
 * 게이트를 적용한다. 크로스 도메인 정책 거부·미존재는 파사드/도메인이 던지는 예외를 전역 핸들러가
 * problem+json으로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartViewFacade cartViewFacade;
    private final CartCommandFacade cartCommandFacade;

    public CartController(CartViewFacade cartViewFacade, CartCommandFacade cartCommandFacade) {
        this.cartViewFacade = cartViewFacade;
        this.cartCommandFacade = cartCommandFacade;
    }

    /** 본인 장바구니를 변형 현재가·소계·총액과 함께 조회한다. */
    @GetMapping
    public CartResponse getCart(AuthUser authUser) {
        return CartResponse.from(cartViewFacade.getCartView(authUser.memberId()));
    }

    /** 변형을 본인 장바구니에 담는다. 같은 변형은 수량을 합산한다. */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(AuthUser authUser, @Valid @RequestBody AddCartItemRequest request) {
        cartCommandFacade.addItem(authUser.memberId(), request.variantId(), request.quantity());
    }

    /** 라인 수량을 변경한다. 증량은 담기와 같은 자격 게이트를 적용한다. */
    @PatchMapping("/items/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeItemQuantity(
            AuthUser authUser,
            @PathVariable UUID variantId,
            @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        cartCommandFacade.changeItemQuantity(authUser.memberId(), variantId, request.quantity());
    }

    /** 라인을 제거한다. */
    @DeleteMapping("/items/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(AuthUser authUser, @PathVariable UUID variantId) {
        cartCommandFacade.removeItem(authUser.memberId(), variantId);
    }

    /** 본인 장바구니를 비운다. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(AuthUser authUser) {
        cartCommandFacade.clear(authUser.memberId());
    }
}

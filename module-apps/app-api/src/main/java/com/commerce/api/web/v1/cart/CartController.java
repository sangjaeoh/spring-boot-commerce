package com.commerce.api.web.v1.cart;

import com.commerce.api.facade.CartCommandFacade;
import com.commerce.api.facade.CartViewFacade;
import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.cart.request.AddCartItemRequest;
import com.commerce.api.web.v1.cart.request.ChangeCartItemQuantityRequest;
import com.commerce.api.web.v1.cart.response.CartResponse;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 장바구니 조회·쓰기 엔드포인트다. */
@Tag(name = "장바구니", description = "장바구니 조회·담기·수량 변경·제거·비우기")
@Authenticated
@RestController
@RequestMapping("/api/v1/carts")
public class CartController {

    private final CartViewFacade cartViewFacade;
    private final CartCommandFacade cartCommandFacade;

    public CartController(CartViewFacade cartViewFacade, CartCommandFacade cartCommandFacade) {
        this.cartViewFacade = cartViewFacade;
        this.cartCommandFacade = cartCommandFacade;
    }

    @Operation(summary = "장바구니 조회", description = "본인 장바구니를 변형 현재가·소계·총액과 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public CartResponse getCart(AuthUser authUser) {
        return CartResponse.from(cartViewFacade.getCartView(authUser.memberId()));
    }

    @Operation(summary = "장바구니 담기", description = "변형을 본인 장바구니에 담는다. 같은 변형은 수량을 합산한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "담김"),
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
                description = "주문 자격 없음·주문 불가 또는 동시 담기 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(AuthUser authUser, @Valid @RequestBody AddCartItemRequest request) {
        cartCommandFacade.addItem(authUser.memberId(), request.variantId(), request.quantity());
    }

    @Operation(summary = "라인 수량 변경", description = "라인 수량을 변경한다. 증량은 담기와 같은 자격 게이트를 적용한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "변경됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "장바구니 라인 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "증량 시 주문 자격 없음·주문 불가 또는 동시 수정 충돌",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PatchMapping("/items/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeItemQuantity(
            AuthUser authUser,
            @Parameter(description = "장바구니 라인의 변형 ID") @PathVariable UUID variantId,
            @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        cartCommandFacade.changeItemQuantity(authUser.memberId(), variantId, request.quantity());
    }

    @Operation(summary = "라인 제거", description = "본인 장바구니에서 라인을 제거한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "제거됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "장바구니 라인 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/items/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(AuthUser authUser, @Parameter(description = "장바구니 라인의 변형 ID") @PathVariable UUID variantId) {
        cartCommandFacade.removeItem(authUser.memberId(), variantId);
    }

    @Operation(summary = "장바구니 비우기", description = "본인 장바구니를 비운다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "비워짐"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(AuthUser authUser) {
        cartCommandFacade.clear(authUser.memberId());
    }
}

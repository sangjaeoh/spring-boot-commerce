package com.commerce.app.api.web.v1.wishlist.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 찜 추가 요청이다. 찜하는 회원은 토큰 주체에서 도출한다. */
@Schema(description = "찜 추가 요청")
public record AddWishlistItemRequest(
        @Schema(description = "찜할 상품 ID") @NotNull UUID productId) {}

package com.commerce.app.api.web.v1.wishlist.response;

import com.commerce.domain.wishlist.application.info.WishlistItemInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "찜 한 건")
public record WishlistItemResponse(
        @Schema(description = "찜한 상품 ID") UUID productId,
        @Schema(description = "찜한 시각") Instant wishedAt) {

    /** 찜 조회 모델에서 응답을 만든다. */
    public static WishlistItemResponse from(WishlistItemInfo info) {
        return new WishlistItemResponse(info.productId(), info.wishedAt());
    }
}

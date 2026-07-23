package com.commerce.app.api.web.v1.wishlist.response;

import com.commerce.domain.wishlist.application.info.WishlistItemInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "찜 목록")
public record WishlistResponse(
        @Schema(description = "찜 목록(최신 찜 우선)") List<WishlistItemResponse> items) {

    public WishlistResponse {
        items = List.copyOf(items);
    }

    /** 찜 조회 모델 목록에서 응답을 만든다. */
    public static WishlistResponse from(List<WishlistItemInfo> items) {
        return new WishlistResponse(
                items.stream().map(WishlistItemResponse::from).toList());
    }
}

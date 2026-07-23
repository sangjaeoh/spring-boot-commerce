package com.commerce.app.api.web.v1.wishlist;

import com.commerce.app.api.web.auth.Authenticated;
import com.commerce.app.api.web.v1.wishlist.request.AddWishlistItemRequest;
import com.commerce.app.api.web.v1.wishlist.response.WishlistResponse;
import com.commerce.common.web.auth.AuthUser;
import com.commerce.domain.wishlist.application.provided.WishlistAppender;
import com.commerce.domain.wishlist.application.provided.WishlistReader;
import com.commerce.domain.wishlist.application.provided.WishlistRemover;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 위시리스트(찜) 조회·쓰기 엔드포인트다. */
@Tag(name = "위시리스트", description = "상품 찜 추가·삭제·목록 조회")
@Authenticated
@RestController
@RequestMapping("/api/v1/wishlists")
public class WishlistController {

    private final WishlistReader wishlistReader;
    private final WishlistAppender wishlistAppender;
    private final WishlistRemover wishlistRemover;

    public WishlistController(
            WishlistReader wishlistReader, WishlistAppender wishlistAppender, WishlistRemover wishlistRemover) {
        this.wishlistReader = wishlistReader;
        this.wishlistAppender = wishlistAppender;
        this.wishlistRemover = wishlistRemover;
    }

    @Operation(summary = "찜 목록 조회", description = "본인의 찜 목록을 최신 찜 우선으로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public WishlistResponse getWishlist(AuthUser authUser) {
        return WishlistResponse.from(wishlistReader.getWishlist(authUser.memberId()));
    }

    @Operation(summary = "찜 추가", description = "상품을 찜한다. 이미 찜한 상품이면 그대로 성공한다(멱등).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "찜됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(AuthUser authUser, @Valid @RequestBody AddWishlistItemRequest request) {
        wishlistAppender.add(authUser.memberId(), request.productId());
    }

    @Operation(summary = "찜 삭제", description = "찜을 해제한다. 찜하지 않은 상품이어도 그대로 성공한다(멱등).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "해제됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(AuthUser authUser, @Parameter(description = "찜한 상품 ID") @PathVariable UUID productId) {
        wishlistRemover.remove(authUser.memberId(), productId);
    }
}

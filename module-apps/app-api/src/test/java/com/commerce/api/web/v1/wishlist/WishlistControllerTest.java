package com.commerce.api.web.v1.wishlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.wishlist.request.AddWishlistItemRequest;
import com.commerce.member.application.MemberAppender;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WishlistControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;

    WishlistControllerTest(MockMvc mvc, ObjectMapper objectMapper, MemberAppender memberAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("찜 추가는 204이고 목록 조회에 상품 ID와 찜 시각이 노출된다")
    void addShowsItemInWishlist() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();

        addWish(memberId, productId).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/wishlists").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].wishedAt").exists());
    }

    @Test
    @DisplayName("같은 상품 재찜도 204이고 목록에는 1건만 남는다 — 찜 추가는 멱등이다")
    void addIsIdempotent() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();

        addWish(memberId, productId).andExpect(status().isNoContent());
        addWish(memberId, productId).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/wishlists").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("찜 삭제는 204이고 목록에서 빠지며, 같은 삭제 재요청도 204다 — 삭제는 멱등이다")
    void removeIsIdempotent() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        addWish(memberId, productId).andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/wishlists/items/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/wishlists").header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(delete("/api/v1/wishlists/items/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("두 회원의 찜은 서로 격리된다")
    void wishlistsAreIsolatedPerMember() throws Exception {
        UUID firstMemberId = registerMember();
        UUID secondMemberId = registerMember();
        UUID productId = UUID.randomUUID();
        addWish(firstMemberId, productId).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/wishlists").header(HttpHeaders.AUTHORIZATION, bearer(secondMemberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("미인증 찜 요청은 401 UNAUTHENTICATED로 거부된다")
    void wishlistRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/wishlists")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/wishlists/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    /** 회원의 찜 추가 요청을 보낸다. */
    private org.springframework.test.web.servlet.ResultActions addWish(UUID memberId, UUID productId) throws Exception {
        return mvc.perform(post("/api/v1/wishlists/items")
                .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddWishlistItemRequest(productId))));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

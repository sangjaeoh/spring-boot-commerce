package com.commerce.api.web.v1.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.review.request.ReviewRequest;
import com.commerce.api.web.v1.review.response.ReviewCreationResponse;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderModifier;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReviewControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final OrderAppender orderAppender;
    private final OrderModifier orderModifier;

    ReviewControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            OrderAppender orderAppender,
            OrderModifier orderModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.orderAppender = orderAppender;
        this.orderModifier = orderModifier;
    }

    @Test
    @DisplayName("배송 완료 상품의 리뷰 작성은 201이고 공개 목록에 별점·본문·작성 시각이 노출된다")
    void writeShowsReviewInPublicList() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        deliverProduct(memberId, productId);

        writeReview(memberId, productId, new ReviewRequest(4, "만족스러운 상품이다."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").exists());

        mvc.perform(get("/api/v1/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].rating").value(4))
                .andExpect(jsonPath("$.reviews[0].content").value("만족스러운 상품이다."))
                .andExpect(jsonPath("$.reviews[0].writtenAt").exists())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("구매확정(배송 완료) 이력이 없는 회원의 작성은 409 API_REVIEW_NOT_ELIGIBLE로 거부된다")
    void writeRejectsMemberWithoutDeliveredPurchase() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        // 결제만 완료(미배송) 주문은 자격이 아니다.
        UUID orderId = placeOrder(memberId, productId);
        orderModifier.markPaid(orderId);

        writeReview(memberId, productId, new ReviewRequest(5, "본문"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("API_REVIEW_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("같은 상품 재작성은 409 REVIEW_ALREADY_WRITTEN으로 거부된다")
    void writeRejectsDuplicateReview() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        deliverProduct(memberId, productId);
        writeReview(memberId, productId, new ReviewRequest(4, "첫 리뷰")).andExpect(status().isCreated());

        writeReview(memberId, productId, new ReviewRequest(5, "둘째 리뷰"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_WRITTEN"));
    }

    @Test
    @DisplayName("본인 리뷰 수정은 204로 목록에 반영되고, 타인 리뷰 수정은 404로 거부된다")
    void reviseAppliesToOwnReviewOnly() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        deliverProduct(memberId, productId);
        UUID reviewId = writeReviewAndGetId(memberId, productId);

        mvc.perform(patch("/api/v1/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRequest(1, "고친 본문"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products/{productId}/reviews", productId))
                .andExpect(jsonPath("$.reviews[0].rating").value(1))
                .andExpect(jsonPath("$.reviews[0].content").value("고친 본문"));

        mvc.perform(patch("/api/v1/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRequest(5, "타인 수정"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("본인 리뷰 삭제는 204이고 목록에서 빠진다")
    void removeDeletesOwnReview() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        deliverProduct(memberId, productId);
        UUID reviewId = writeReviewAndGetId(memberId, productId);

        mvc.perform(delete("/api/v1/reviews/{reviewId}", reviewId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(0));
    }

    @Test
    @DisplayName("목록은 미인증·인증 열람자 모두 조회되고, 작성은 미인증이면 401로 거부된다")
    void listIsPublicAndWriteRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/products/{productId}/reviews", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(0));

        // 인증 열람자도 공개 목록을 본다 — 익명 전용(@Anonymous) 표면이 아니다.
        mvc.perform(get("/api/v1/products/{productId}/reviews", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(0));

        mvc.perform(post("/api/v1/products/{productId}/reviews", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRequest(3, "본문"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("범위 밖 별점 작성은 400 REVIEW_INVALID_RATING으로 거부된다")
    void writeRejectsRatingOutOfRange() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();
        deliverProduct(memberId, productId);

        writeReview(memberId, productId, new ReviewRequest(6, "본문"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INVALID_RATING"));
    }

    /** 회원의 리뷰 작성 요청을 보낸다. */
    private ResultActions writeReview(UUID memberId, UUID productId, ReviewRequest request) throws Exception {
        return mvc.perform(post("/api/v1/products/{productId}/reviews", productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    /** 리뷰를 쓰고 발급된 리뷰 ID를 얻는다. */
    private UUID writeReviewAndGetId(UUID memberId, UUID productId) throws Exception {
        String body = writeReview(memberId, productId, new ReviewRequest(4, "처음 본문"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, ReviewCreationResponse.class).reviewId());
    }

    /** 상품 라인 하나를 담은 배송 완료(PAID·DELIVERED) 주문을 만든다. */
    private void deliverProduct(UUID memberId, UUID productId) {
        UUID orderId = placeOrder(memberId, productId);
        orderModifier.markPaid(orderId);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);
    }

    /** 상품 라인 하나를 담은 결제 전 주문을 만든다. */
    private UUID placeOrder(UUID memberId, UUID productId) {
        OrderLineSnapshot line = new OrderLineSnapshot(UUID.randomUUID(), productId, "상품", null, Money.of(10000L), 1);
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(memberId, List.of(line), address, Money.ZERO, Money.ZERO, null);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

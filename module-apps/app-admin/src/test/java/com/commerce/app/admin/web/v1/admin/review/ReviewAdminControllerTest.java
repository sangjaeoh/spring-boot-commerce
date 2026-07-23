package com.commerce.app.admin.web.v1.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.admin.web.v1.WebIntegrationTest;
import com.commerce.app.admin.web.v1.admin.review.request.ReviewRemovalRequest;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.review.application.info.ReviewInfo;
import com.commerce.domain.review.application.provided.ReviewAppender;
import com.commerce.domain.review.application.provided.ReviewReader;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReviewAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final ReviewAppender reviewAppender;
    private final ReviewReader reviewReader;

    ReviewAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            ReviewAppender reviewAppender,
            ReviewReader reviewReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.reviewAppender = reviewAppender;
        this.reviewReader = reviewReader;
    }

    @Test
    @DisplayName("관리자 리뷰 제거는 204이고 상품별 공개 목록에서 빠진다")
    void removeExcludesReviewFromPublicList() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID reviewId = reviewAppender.write(registerMember(), productId, 1, "부적절한 본문");

        mvc.perform(delete("/api/v1/admin/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRemovalRequest("욕설 포함"))))
                .andExpect(status().isNoContent());

        assertThat(reviewReader.getProductPage(productId, PageRequest.of(0, 10)).getContent())
                .extracting(ReviewInfo::id)
                .doesNotContain(reviewId);
    }

    @Test
    @DisplayName("일반 회원의 관리자 리뷰 제거는 403으로 거부된다")
    void removeRejectsNonAdmin() throws Exception {
        UUID memberId = registerMember();
        UUID reviewId = reviewAppender.write(memberId, UUID.randomUUID(), 1, "부적절한 본문");

        mvc.perform(delete("/api/v1/admin/reviews/{reviewId}", reviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRemovalRequest("사유"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공백 사유의 관리자 리뷰 제거는 400으로 거부된다")
    void removeRejectsBlankReason() throws Exception {
        mvc.perform(delete("/api/v1/admin/reviews/{reviewId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewRemovalRequest(" "))))
                .andExpect(status().isBadRequest());
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

package com.commerce.api.web.v1.inquiry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.inquiry.request.InquiryRequest;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.service.MemberAppender;
import java.util.Map;
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
class InquiryControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final JwtTokenCodec jwtTokenCodec;

    InquiryControllerTest(
            MockMvc mvc, ObjectMapper objectMapper, MemberAppender memberAppender, JwtTokenCodec jwtTokenCodec) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.jwtTokenCodec = jwtTokenCodec;
    }

    @Test
    @DisplayName("문의 작성은 201이고 공개 목록에 본문·미답변 상태로 노출된다")
    void writeShowsInquiryInPublicList() throws Exception {
        UUID memberId = registerMember();
        UUID productId = UUID.randomUUID();

        writeInquiry(memberId, productId, new InquiryRequest("배송은 얼마나 걸리나요?", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").exists());

        mvc.perform(get("/api/v1/products/{productId}/inquiries", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries.length()").value(1))
                .andExpect(jsonPath("$.inquiries[0].content").value("배송은 얼마나 걸리나요?"))
                .andExpect(jsonPath("$.inquiries[0].secret").value(false))
                .andExpect(jsonPath("$.inquiries[0].answer").doesNotExist())
                .andExpect(jsonPath("$.inquiries[0].writtenAt").exists())
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    @DisplayName("타인·미인증의 비밀글 조회는 존재만 보이고 본문이 노출되지 않으며, 작성자 조회는 본문이 보인다")
    void secretContentIsHiddenFromOthers() throws Exception {
        UUID authorId = registerMember();
        UUID productId = UUID.randomUUID();
        writeInquiry(authorId, productId, new InquiryRequest("비밀 문의 본문", true)).andExpect(status().isCreated());

        // 미인증 조회 — 본문 미노출
        mvc.perform(get("/api/v1/products/{productId}/inquiries", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries[0].secret").value(true))
                .andExpect(jsonPath("$.inquiries[0].content").doesNotExist());

        // 타인 조회 — 본문 미노출
        mvc.perform(get("/api/v1/products/{productId}/inquiries", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries[0].content").doesNotExist());

        // 작성자 조회 — 본문 노출
        mvc.perform(get("/api/v1/products/{productId}/inquiries", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries[0].content").value("비밀 문의 본문"));
    }

    @Test
    @DisplayName("관리자 조회는 타인 비밀글 본문도 보인다")
    void adminSeesSecretContent() throws Exception {
        UUID authorId = registerMember();
        UUID productId = UUID.randomUUID();
        writeInquiry(authorId, productId, new InquiryRequest("비밀 문의 본문", true)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/products/{productId}/inquiries", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries[0].content").value("비밀 문의 본문"));
    }

    @Test
    @DisplayName("미인증 작성은 401로 거부되고, 미인증 목록 조회는 200이다")
    void writeRequiresAuthenticationAndListIsPublic() throws Exception {
        mvc.perform(post("/api/v1/products/{productId}/inquiries", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InquiryRequest("본문", false))))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/products/{productId}/inquiries", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiries.length()").value(0));
    }

    /** 회원의 문의 작성 요청을 보낸다. */
    private ResultActions writeInquiry(UUID memberId, UUID productId, InquiryRequest request) throws Exception {
        return mvc.perform(post("/api/v1/products/{productId}/inquiries", productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    /** ADMIN 역할 회원을 등록하고 그 주체의 관리자 토큰 헤더 값을 만든다 — 관리자 시딩은 app-admin 소유라 직접 만든다. */
    private String adminBearer() {
        UUID adminId = memberAppender.registerAdmin(
                "admin-" + UUID.randomUUID() + "@example.com", "관리자", "admin-password-123!");
        return "Bearer " + jwtTokenCodec.issue(adminId.toString(), Map.of("role", "ADMIN"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

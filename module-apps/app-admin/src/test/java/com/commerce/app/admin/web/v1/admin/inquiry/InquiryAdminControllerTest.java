package com.commerce.app.admin.web.v1.admin.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.admin.web.v1.WebIntegrationTest;
import com.commerce.app.admin.web.v1.admin.inquiry.request.InquiryAnswerRequest;
import com.commerce.domain.inquiry.application.provided.InquiryAppender;
import com.commerce.domain.inquiry.application.provided.InquiryReader;
import com.commerce.domain.member.application.provided.MemberAppender;
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
class InquiryAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final InquiryAppender inquiryAppender;
    private final InquiryReader inquiryReader;

    InquiryAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            InquiryAppender inquiryAppender,
            InquiryReader inquiryReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.inquiryAppender = inquiryAppender;
        this.inquiryReader = inquiryReader;
    }

    @Test
    @DisplayName("관리자 답변 등록은 204이고 노출 경로(문의 리더)에 답변이 실린다")
    void answerShowsInPublicList() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID inquiryId = inquiryAppender.write(registerMember(), productId, "재입고 예정이 있나요?", false);

        mvc.perform(post("/api/v1/admin/inquiries/{inquiryId}/answer", inquiryId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InquiryAnswerRequest("다음 주 재입고 예정입니다."))))
                .andExpect(status().isNoContent());

        assertThat(inquiryReader
                        .getProductPage(productId, PageRequest.of(0, 20))
                        .getContent()
                        .get(0)
                        .answer())
                .isEqualTo("다음 주 재입고 예정입니다.");
    }

    @Test
    @DisplayName("일반 회원의 답변 등록은 403으로 거부된다")
    void answerRejectsNonAdmin() throws Exception {
        UUID memberId = registerMember();
        UUID inquiryId = inquiryAppender.write(memberId, UUID.randomUUID(), "문의 본문", false);

        mvc.perform(post("/api/v1/admin/inquiries/{inquiryId}/answer", inquiryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InquiryAnswerRequest("답변"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 문의의 답변 등록은 404 INQUIRY_NOT_FOUND로 거부된다")
    void answerRejectsUnknownInquiry() throws Exception {
        mvc.perform(post("/api/v1/admin/inquiries/{inquiryId}/answer", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InquiryAnswerRequest("답변"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

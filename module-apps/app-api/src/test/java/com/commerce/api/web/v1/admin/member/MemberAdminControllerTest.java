package com.commerce.api.web.v1.admin.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.member.request.MemberSuspensionRequest;
import com.commerce.api.web.v1.member.request.MemberWithdrawalRequest;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberReader;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberModifier memberModifier;

    MemberAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            MemberAppender memberAppender,
            MemberReader memberReader,
            MemberModifier memberModifier) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
    }

    @Test
    @DisplayName("관리자의 회원 상세 조회는 200으로 ACTIVE 상태·이메일·이름을 싣는다")
    void getMemberReturnsActiveMember() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        mvc.perform(get("/api/v1/admin/members/{memberId}", memberId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("테스터"));
    }

    @Test
    @DisplayName("정지 회원 상세는 SUSPENDED 상태·정지 사유를 싣는다")
    void getMemberIncludesSuspension() throws Exception {
        UUID memberId = registerMember();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        mvc.perform(get("/api/v1/admin/members/{memberId}", memberId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("없는 회원 상세 조회는 404 MEMBER_NOT_FOUND로 거부된다")
    void getMemberReturns404ForMissingMember() throws Exception {
        mvc.perform(get("/api/v1/admin/members/{memberId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("구매자 토큰의 회원 지정 조회는 403 FORBIDDEN으로 거부된다")
    void getMemberRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();

        mvc.perform(get("/api/v1/admin/members/{memberId}", buyerId).header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("구매자 토큰의 회원 정지는 403 FORBIDDEN으로 거부된다")
    void suspendRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();
        UUID targetId = registerMember();

        mvc.perform(post("/api/v1/admin/members/{memberId}/suspend", targetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberSuspensionRequest(SuspensionReason.FRAUD_SUSPECTED))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(memberReader.getMember(targetId).status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("미인증 회원 정지는 401 UNAUTHENTICATED로 거부된다")
    void suspendRejectsUnauthenticated() throws Exception {
        UUID targetId = registerMember();

        mvc.perform(post("/api/v1/admin/members/{memberId}/suspend", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberSuspensionRequest(SuspensionReason.FRAUD_SUSPECTED))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("정지 사유가 없는 요청은 400 VALIDATION_FAILED 구조 응답으로 거부된다")
    void suspendRejectsMissingReason() throws Exception {
        UUID targetId = registerMember();

        mvc.perform(post("/api/v1/admin/members/{memberId}/suspend", targetId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("관리자의 정지 후 해제 왕복이 사유를 기록했다가 지운다")
    void suspendAndReinstateRoundTrip() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(post("/api/v1/admin/members/{memberId}/suspend", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberSuspensionRequest(SuspensionReason.FRAUD_SUSPECTED))))
                .andExpect(status().isNoContent());
        MemberInfo suspended = memberReader.getMember(memberId);
        assertThat(suspended.status()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo(SuspensionReason.FRAUD_SUSPECTED);

        mvc.perform(post("/api/v1/admin/members/{memberId}/reinstate", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        MemberInfo reinstated = memberReader.getMember(memberId);
        assertThat(reinstated.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reinstated.suspensionReason()).isNull();
    }

    @Test
    @DisplayName("활성 회원 해제는 409 MEMBER_INVALID_STATUS_TRANSITION으로 거부된다")
    void reinstateRejectsActiveMember() throws Exception {
        UUID memberId = registerMember();

        mvc.perform(post("/api/v1/admin/members/{memberId}/reinstate", memberId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("관리자의 이메일 검색은 200으로 정확 일치 활성 회원을 싣는다")
    void searchByEmailReturnsMatchingMember() throws Exception {
        String email = "search-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");

        mvc.perform(get("/api/v1/admin/members").param("email", email).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("일치하는 회원이 없는 이메일 검색은 404 MEMBER_NOT_FOUND로 거부된다")
    void searchByEmailReturns404ForMissingMember() throws Exception {
        mvc.perform(get("/api/v1/admin/members")
                        .param("email", "missing-" + UUID.randomUUID() + "@example.com")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("탈퇴 회원의 이메일 검색은 404 MEMBER_NOT_FOUND로 미존재 취급된다")
    void searchByEmailExcludesWithdrawnMember() throws Exception {
        String email = "withdrawn-" + UUID.randomUUID() + "@example.com";
        UUID memberId = memberAppender.register(email, "테스터", "password-123!");
        mvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MemberWithdrawalRequest(WithdrawalReason.NO_LONGER_USED))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/members").param("email", email).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("구매자 토큰의 이메일 검색은 403 FORBIDDEN으로 거부된다")
    void searchByEmailRejectsBuyerToken() throws Exception {
        UUID buyerId = registerMember();

        mvc.perform(get("/api/v1/admin/members")
                        .param("email", "any-" + UUID.randomUUID() + "@example.com")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 이메일 검색은 401 UNAUTHENTICATED로 거부된다")
    void searchByEmailRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/members").param("email", "any-" + UUID.randomUUID() + "@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }
}

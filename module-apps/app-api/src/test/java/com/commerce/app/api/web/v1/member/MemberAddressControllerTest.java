package com.commerce.app.api.web.v1.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.api.web.v1.WebIntegrationTest;
import com.commerce.app.api.web.v1.member.request.MemberAddressRequest;
import com.commerce.app.api.web.v1.member.response.MemberAddressCreationResponse;
import com.commerce.domain.member.application.provided.MemberAppender;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberAddressControllerTest extends WebIntegrationTest {

    private static final String BASE = "/api/v1/members/me/addresses";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;

    MemberAddressControllerTest(MockMvc mvc, ObjectMapper objectMapper, MemberAppender memberAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("첫 배송지는 자동 기본이 되고 목록이 기본 우선으로 반환된다")
    void firstAddressBecomesDefaultAndListPutsDefaultFirst() throws Exception {
        UUID memberId = registerMember();
        UUID firstId = addAddress(memberId, "김민준");
        addAddress(memberId, "이서연");

        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].addressId").value(firstId.toString()))
                .andExpect(jsonPath("$.addresses[0].defaultAddress").value(true))
                .andExpect(jsonPath("$.addresses[1].defaultAddress").value(false));
    }

    @Test
    @DisplayName("기본 지정이 204로 기본 배송지를 옮기고 기본은 항상 하나다")
    void designateDefaultMovesSingleDefault() throws Exception {
        UUID memberId = registerMember();
        addAddress(memberId, "김민준");
        UUID secondId = addAddress(memberId, "이서연");

        mvc.perform(post(BASE + "/{addressId}/default", secondId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].addressId").value(secondId.toString()))
                .andExpect(jsonPath("$.addresses[0].defaultAddress").value(true))
                .andExpect(jsonPath("$.addresses[1].defaultAddress").value(false));
    }

    @Test
    @DisplayName("배송지 수정이 204로 필드를 반영한다")
    void reviseAppliesFields() throws Exception {
        UUID memberId = registerMember();
        UUID addressId = addAddress(memberId, "김민준");

        MemberAddressRequest revised =
                new MemberAddressRequest("박지후", "06236", "서울특별시 강남구 테헤란로 427", null, "010-9876-5432");
        mvc.perform(patch(BASE + "/{addressId}", addressId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revised)))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(jsonPath("$.addresses[0].recipientName").value("박지후"))
                .andExpect(jsonPath("$.addresses[0].zipCode").value("06236"))
                .andExpect(jsonPath("$.addresses[0].detailAddress").doesNotExist());
    }

    @Test
    @DisplayName("배송지 삭제가 204로 목록에서 제거한다")
    void purgeRemovesAddress() throws Exception {
        UUID memberId = registerMember();
        addAddress(memberId, "김민준");
        UUID secondId = addAddress(memberId, "이서연");

        mvc.perform(delete(BASE + "/{addressId}", secondId).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(jsonPath("$.addresses.length()").value(1));
    }

    @Test
    @DisplayName("타인 배송지의 수정·삭제·기본 지정은 404 MEMBER_ADDRESS_NOT_FOUND로 거부된다")
    void rejectsOthersAddress() throws Exception {
        UUID ownerId = registerMember();
        UUID addressId = addAddress(ownerId, "김민준");
        UUID otherId = registerMember();

        mvc.perform(patch(BASE + "/{addressId}", addressId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("박지후"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));

        mvc.perform(delete(BASE + "/{addressId}", addressId).header(HttpHeaders.AUTHORIZATION, bearer(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));

        mvc.perform(post(BASE + "/{addressId}/default", addressId).header(HttpHeaders.AUTHORIZATION, bearer(otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));
    }

    @Test
    @DisplayName("11번째 배송지 등록은 409 MEMBER_ADDRESS_LIMIT_EXCEEDED로 거부된다")
    void rejectsBeyondLimit() throws Exception {
        UUID memberId = registerMember();
        for (int i = 0; i < 10; i++) {
            addAddress(memberId, "수령인" + i);
        }

        mvc.perform(post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("초과"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("미인증 배송지 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID addAddress(UUID memberId, String recipientName) throws Exception {
        String body = mvc.perform(post(BASE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(recipientName))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper
                .readValue(body, MemberAddressCreationResponse.class)
                .addressId());
    }

    private static MemberAddressRequest request(String recipientName) {
        return new MemberAddressRequest(recipientName, "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

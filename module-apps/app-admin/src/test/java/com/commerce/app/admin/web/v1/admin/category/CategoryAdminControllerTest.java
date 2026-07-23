package com.commerce.app.admin.web.v1.admin.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.app.admin.web.v1.WebIntegrationTest;
import com.commerce.app.admin.web.v1.admin.category.request.CategoryCreationRequest;
import com.commerce.app.admin.web.v1.admin.category.request.CategoryRenameRequest;
import com.commerce.app.admin.web.v1.admin.category.response.CategoryCreationResponse;
import com.commerce.app.admin.web.v1.admin.category.response.CategoryListResponse;
import com.commerce.app.admin.web.v1.admin.category.response.CategoryResponse;
import com.commerce.domain.member.application.provided.MemberAppender;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CategoryAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final MemberAppender memberAppender;

    CategoryAdminControllerTest(MockMvc mvc, ObjectMapper objectMapper, MemberAppender memberAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.memberAppender = memberAppender;
    }

    @Test
    @DisplayName("생성·목록·이름 변경·삭제 CRUD가 각각 201·200·204·204로 순환한다")
    void crudFlowCreatesRenamesAndDeletes() throws Exception {
        String name = "가전-" + UUID.randomUUID();
        UUID categoryId = createCategory(name);

        assertThat(findCategory(categoryId)).map(CategoryResponse::name).contains(name);

        String renamed = "생활가전-" + UUID.randomUUID();
        mvc.perform(patch("/api/v1/admin/categories/{categoryId}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRenameRequest(renamed))))
                .andExpect(status().isNoContent());
        assertThat(findCategory(categoryId)).map(CategoryResponse::name).contains(renamed);

        mvc.perform(delete("/api/v1/admin/categories/{categoryId}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(findCategory(categoryId)).isEmpty();
    }

    @Test
    @DisplayName("없는 카테고리의 이름 변경·삭제는 404 PRODUCT_CATEGORY_NOT_FOUND로 거부된다")
    void renameAndDeleteRejectMissingCategory() throws Exception {
        mvc.perform(patch("/api/v1/admin/categories/{categoryId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRenameRequest("이름"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_FOUND"));

        mvc.perform(delete("/api/v1/admin/categories/{categoryId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("공백 이름 생성은 400, 구매자 토큰 생성은 403으로 거부된다")
    void createRejectsBlankNameAndBuyerToken() throws Exception {
        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreationRequest("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        UUID buyerId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreationRequest("가전"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** 카테고리를 생성하고 새 카테고리 ID를 파싱한다. */
    private UUID createCategory(String name) throws Exception {
        String body = mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreationRequest(name))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, CategoryCreationResponse.class).categoryId());
    }

    /** 관리자 목록에서 카테고리 행을 ID로 찾는다. 공유 DB라 다른 테스트의 행이 섞여 있어 ID로 좁힌다. */
    private Optional<CategoryResponse> findCategory(UUID categoryId) throws Exception {
        String body = mvc.perform(get("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, CategoryListResponse.class).categories().stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst();
    }
}

package com.commerce.admin.web.v1.admin.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.facade.ProductRegistrationFacade;
import com.commerce.admin.web.v1.WebIntegrationTest;
import com.commerce.admin.web.v1.admin.product.response.ProductImageUploadResponse;
import com.commerce.member.service.MemberAppender;
import com.commerce.product.info.ProductImageInfo;
import com.commerce.product.service.ProductImageReader;
import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductImageAdminControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final ProductImageReader productImageReader;

    ProductImageAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            ProductImageReader productImageReader) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.productImageReader = productImageReader;
    }

    @Test
    @DisplayName("업로드한 이미지가 순서대로 노출 경로(이미지 리더)에 실린다")
    void uploadedImagesExposedOnDetailAndCatalog() throws Exception {
        String name = "이미지상품-" + UUID.randomUUID();
        UUID productId = seedExposedProduct(name);

        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(pngFile("first.png"))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageId").exists());
        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(new MockMultipartFile("image", "second.jpg", "image/jpeg", new byte[] {9, 9}))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isCreated());

        List<ProductImageInfo> images = productImageReader.getByProductId(productId);
        assertThat(images).hasSize(2);
        assertThat(images.get(0).sortOrder()).isLessThan(images.get(1).sortOrder());
        assertThat(images.get(0).url()).isNotBlank();
    }

    @Test
    @DisplayName("허용되지 않은 형식 업로드는 400 PRODUCT_UNSUPPORTED_IMAGE_FORMAT로 거부된다")
    void uploadRejectsUnsupportedFormat() throws Exception {
        UUID productId = seedExposedProduct("이미지상품-" + UUID.randomUUID());

        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(new MockMultipartFile("image", "note.txt", "text/plain", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNSUPPORTED_IMAGE_FORMAT"));

        assertThat(productImageReader.getByProductId(productId)).isEmpty();
    }

    @Test
    @DisplayName("5MB 초과 업로드는 400 PRODUCT_IMAGE_TOO_LARGE로 거부된다")
    void uploadRejectsOversizedImage() throws Exception {
        UUID productId = seedExposedProduct("이미지상품-" + UUID.randomUUID());
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];

        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(new MockMultipartFile("image", "big.png", "image/png", oversized))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("이미지 삭제는 204로 노출을 제거하고 재삭제는 404다")
    void deleteRemovesExposure() throws Exception {
        UUID productId = seedExposedProduct("이미지상품-" + UUID.randomUUID());
        String body = mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(pngFile("only.png"))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID imageId = UUID.fromString(
                objectMapper.readValue(body, ProductImageUploadResponse.class).imageId());

        mvc.perform(delete("/api/v1/admin/products/{productId}/images/{imageId}", productId, imageId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        assertThat(productImageReader.getByProductId(productId)).isEmpty();
        mvc.perform(delete("/api/v1/admin/products/{productId}/images/{imageId}", productId, imageId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 상품 업로드는 404, 구매자 토큰 업로드는 403으로 거부된다")
    void uploadRejectsMissingProductAndBuyerToken() throws Exception {
        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", UUID.randomUUID())
                        .file(pngFile("orphan.png"))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        UUID productId = seedExposedProduct("이미지상품-" + UUID.randomUUID());
        UUID buyerId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        mvc.perform(multipart("/api/v1/admin/products/{productId}/images", productId)
                        .file(pngFile("buyer.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static MockMultipartFile pngFile(String filename) {
        return new MockMultipartFile("image", filename, "image/png", new byte[] {1, 2, 3});
    }

    private UUID seedExposedProduct(String name) {
        // registerProduct가 판매 시작(ON_SALE)까지 수행한다.
        return productRegistrationFacade.registerProduct(name, null, Money.of(10000L), List.of(), 10);
    }
}

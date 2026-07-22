package com.commerce.admin.web.v1.admin.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.admin.web.v1.WebIntegrationTest;
import com.commerce.admin.web.v1.admin.product.request.OptionRequest;
import com.commerce.admin.web.v1.admin.product.request.ProductEditRequest;
import com.commerce.admin.web.v1.admin.product.request.ProductRegistrationRequest;
import com.commerce.admin.web.v1.admin.product.request.VariantRegistrationRequest;
import com.commerce.admin.web.v1.admin.product.response.ProductRegistrationResponse;
import com.commerce.admin.web.v1.admin.product.response.VariantRegistrationResponse;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderReader;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.service.CategoryAppender;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
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
class ProductAdminControllerTest extends WebIntegrationTest {

    private static final String KEY_HEADER = "Idempotency-Key";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final MemberAppender memberAppender;
    private final OrderReader orderReader;
    private final CategoryAppender categoryAppender;

    ProductAdminControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            ProductReader productReader,
            ProductVariantReader variantReader,
            MemberAppender memberAppender,
            OrderReader orderReader,
            CategoryAppender categoryAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.memberAppender = memberAppender;
        this.orderReader = orderReader;
        this.categoryAppender = categoryAppender;
    }

    @Test
    @DisplayName("카테고리 지정 등록·편집 재지정이 공개 노출 필터와 관리자 목록에 반영된다")
    void categoryAssignmentOnRegistrationAndEdit() throws Exception {
        UUID firstCategoryId = categoryAppender.create("분류A-" + UUID.randomUUID());
        UUID secondCategoryId = categoryAppender.create("분류B-" + UUID.randomUUID());
        String name = "분류상품-" + UUID.randomUUID();
        ProductRegistrationRequest request =
                new ProductRegistrationRequest(name, null, firstCategoryId, 10000L, List.of(), 5);
        String body = mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID productId = UUID.fromString(
                objectMapper.readValue(body, ProductRegistrationResponse.class).productId());

        assertThat(exposedProductIds(firstCategoryId)).containsExactly(productId);
        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].categoryId").value(firstCategoryId.toString()));

        mvc.perform(patch("/api/v1/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductEditRequest(name, null, secondCategoryId))))
                .andExpect(status().isNoContent());

        assertThat(exposedProductIds(secondCategoryId)).containsExactly(productId);
        assertThat(exposedProductIds(firstCategoryId)).isEmpty();
    }

    @Test
    @DisplayName("없는 카테고리 지정 등록·편집은 404 PRODUCT_CATEGORY_NOT_FOUND로 거부된다")
    void categoryAssignmentRejectsMissingCategory() throws Exception {
        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductRegistrationRequest("셔츠", null, UUID.randomUUID(), 10000L, List.of(), 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_FOUND"));

        UUID productId = registerProductViaHttp("무분류셔츠", 10000L, 5);
        mvc.perform(patch("/api/v1/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProductEditRequest("무분류셔츠", null, UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("상품 등록이 201로 상품 ID를 반환하고 ON_SALE 상품·옵션 변형을 시딩한다")
    void registerReturnsProductId() throws Exception {
        ProductRegistrationRequest request = new ProductRegistrationRequest(
                "티셔츠", "면 100%", null, 10000L, List.of(new OptionRequest("색상", "빨강")), 50);

        String body = mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID productId = UUID.fromString(
                objectMapper.readValue(body, ProductRegistrationResponse.class).productId());
        assertThat(productReader.getProduct(productId).status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(variantReader.getByProductId(productId).get(0).optionLabel()).isEqualTo("빨강");
    }

    @Test
    @DisplayName("빈 상품명은 400 problem+json으로 거부된다")
    void registerRejectsBlankName() throws Exception {
        ProductRegistrationRequest request = new ProductRegistrationRequest("  ", null, null, 10000L, List.of(), 50);

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("옵션 배열에 null 원소가 있으면 400으로 거부된다(500 아님)")
    void registerRejectsNullOptionElement() throws Exception {
        String json = """
                {"name":"모자","price":5000,"options":[null],"initialQuantity":10}""";

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("추가 변형 등록이 201로 변형 ID를 반환하고 두 변형이 노출되며 새 변형으로 주문이 생성된다")
    void addVariantExposesBothVariantsAndIsOrderable() throws Exception {
        UUID productId = registerProductViaHttp("옵션셔츠", 10000L, 5);
        UUID variantId = addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);

        assertThat(variantReader.getByProductId(productId)).hasSize(2);

        UUID memberId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        UUID orderId = placePaidOrder(memberId, variantId, 2);
        assertThat(orderReader.getOrder(orderId).lines().get(0).variantId()).isEqualTo(variantId);
    }

    @Test
    @DisplayName("비-RETIRED 변형과 같은 옵션 조합의 추가 등록은 409 PRODUCT_VARIANT_DUPLICATE_OPTION으로 거부된다")
    void addVariantRejectsDuplicateOptionCombination() throws Exception {
        UUID productId = registerProductViaHttp("중복셔츠", 10000L, 5);
        addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);

        VariantRegistrationRequest duplicate =
                new VariantRegistrationRequest(13000L, List.of(new OptionRequest("색상", "파랑")), 1);
        mvc.perform(post("/api/v1/admin/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_DUPLICATE_OPTION"));
    }

    @Test
    @DisplayName("옵션 값이 한도를 넘으면 409 중복이 아니라 400 VALIDATION_FAILED로 거부된다")
    void addVariantRejectsOversizedOptionValue() throws Exception {
        UUID productId = registerProductViaHttp("길이초과셔츠", 10000L, 5);

        VariantRegistrationRequest oversized =
                new VariantRegistrationRequest(12000L, List.of(new OptionRequest("색상", "가".repeat(41))), 1);
        mvc.perform(post("/api/v1/admin/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("은퇴한 옵션 조합은 새 변형으로 재등록된다")
    void addVariantAllowsRetiredCombinationAgain() throws Exception {
        UUID productId = registerProductViaHttp("재등록셔츠", 10000L, 5);
        UUID variantId = addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);
        mvc.perform(post("/api/v1/admin/product-variants/{variantId}/retire", variantId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        UUID reRegistered = addVariantViaHttp(productId, 15000L, List.of(new OptionRequest("색상", "파랑")), 2);

        assertThat(reRegistered).isNotEqualTo(variantId);
    }

    @Test
    @DisplayName("없는 상품의 추가 변형 등록은 404 PRODUCT_NOT_FOUND로 거부된다")
    void addVariantReturns404ForMissingProduct() throws Exception {
        VariantRegistrationRequest request = new VariantRegistrationRequest(10000L, List.of(), 1);

        mvc.perform(post("/api/v1/admin/products/{productId}/variants", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("가격 0의 추가 변형 등록은 400 VALIDATION_FAILED로 거부된다")
    void addVariantRejectsNonPositivePrice() throws Exception {
        UUID productId = registerProductViaHttp("검증셔츠", 10000L, 5);

        mvc.perform(post("/api/v1/admin/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0,\"options\":[],\"initialQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("숨김·재노출 왕복이 각각 204로 상태를 전환한다")
    void hideAndShowRoundTrip() throws Exception {
        UUID productId = registerProductViaHttp("전환셔츠", 10000L, 5);

        mvc.perform(post("/api/v1/admin/products/{productId}/hide", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(productReader.getProduct(productId).status()).isEqualTo(ProductStatus.HIDDEN);

        mvc.perform(post("/api/v1/admin/products/{productId}/show", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(productReader.getProduct(productId).status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("ON_SALE 상품의 show·HIDDEN 상품의 hide 요청은 409 PRODUCT_INVALID_STATE_TRANSITION으로 거부된다")
    void showAndHideRejectInvalidTransition() throws Exception {
        UUID productId = registerProductViaHttp("전이거부셔츠", 10000L, 5);

        mvc.perform(post("/api/v1/admin/products/{productId}/show", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_INVALID_STATE_TRANSITION"));

        mvc.perform(post("/api/v1/admin/products/{productId}/hide", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/products/{productId}/hide", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("편집이 204로 이름·설명을 바꾸고 기존 주문 productName 스냅샷은 불변이다")
    void editDoesNotAffectExistingOrderSnapshot() throws Exception {
        UUID productId = registerProductViaHttp("원래셔츠", 10000L, 5);
        UUID variantId = variantReader.getByProductId(productId).get(0).id();
        UUID memberId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        UUID orderId = placePaidOrder(memberId, variantId, 1);

        ProductEditRequest edit = new ProductEditRequest("바뀐셔츠", "새 설명", null);
        mvc.perform(patch("/api/v1/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isNoContent());

        assertThat(productReader.getProduct(productId).name()).isEqualTo("바뀐셔츠");
        assertThat(productReader.getProduct(productId).description()).isEqualTo("새 설명");
        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.lines().get(0).productName()).isEqualTo("원래셔츠");
    }

    @Test
    @DisplayName("논리삭제가 204로 성공하고 이후 조회는 PRODUCT_NOT_FOUND로 거부된다")
    void deleteRemovesProductFromDetail() throws Exception {
        UUID productId = registerProductViaHttp("삭제셔츠", 10000L, 5);

        mvc.perform(delete("/api/v1/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> productReader.getProduct(productId)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("구매자 토큰의 상품 등록은 403 FORBIDDEN으로 거부된다")
    void registerRejectsBuyerToken() throws Exception {
        UUID buyerId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
        ProductRegistrationRequest request = new ProductRegistrationRequest("셔츠", null, null, 10000L, List.of(), 5);

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자 상품 목록은 200으로 숨김 상품을 포함해 최신 등록순 페이지로 싣는다")
    void adminListIncludesHiddenProduct() throws Exception {
        UUID productId = registerProductViaHttp("관리목록셔츠", 10000L, 5);

        mvc.perform(get("/api/v1/admin/products")
                        .param("page", "1")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());

        mvc.perform(post("/api/v1/admin/products/{productId}/hide", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].status").value("HIDDEN"));
    }

    @Test
    @DisplayName("구매자 토큰의 관리자 상품 목록 조회는 403 FORBIDDEN으로 거부된다")
    void adminListRejectsBuyerToken() throws Exception {
        UUID buyerId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");

        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, bearer(buyerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 관리자 상품 목록 조회는 401 UNAUTHENTICATED로 거부된다")
    void adminListRejectsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 409 DUPLICATE_REQUEST로 거부된다")
    void duplicateIdempotencyKeyRejected() throws Exception {
        ProductRegistrationRequest request = new ProductRegistrationRequest("모자", null, null, 5000L, List.of(), 10);
        String json = objectMapper.writeValueAsString(request);
        String key = "product-" + UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    /** 공개 노출 페이지(카탈로그 필터와 같은 리더 경로)에 실리는 상품 ID 목록을 조회한다. */
    private List<UUID> exposedProductIds(UUID categoryId) {
        return productReader.getExposedPage(null, categoryId, PageRequest.of(0, 20)).getContent().stream()
                .map(ProductInfo::id)
                .toList();
    }

    private UUID registerProductViaHttp(String name, long price, int initialQuantity) throws Exception {
        ProductRegistrationRequest request =
                new ProductRegistrationRequest(name, null, null, price, List.of(), initialQuantity);
        String body = mvc.perform(post("/api/v1/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, ProductRegistrationResponse.class).productId());
    }

    private UUID addVariantViaHttp(UUID productId, long price, List<OptionRequest> options, int initialQuantity)
            throws Exception {
        VariantRegistrationRequest request = new VariantRegistrationRequest(price, options, initialQuantity);
        String body = mvc.perform(post("/api/v1/admin/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variantId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(
                objectMapper.readValue(body, VariantRegistrationResponse.class).variantId());
    }
}

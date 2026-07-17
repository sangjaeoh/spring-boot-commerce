package com.commerce.api.web.v1.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.api.web.v1.admin.product.request.ProductRegistrationRequest;
import com.commerce.api.web.v1.admin.product.response.ProductRegistrationResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    ProductControllerTest(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("상품 상세 조회는 200으로 ACTIVE 변형·주문가능·대표가를 싣는다")
    void getProductReturnsDetailWithOrderableVariant() throws Exception {
        UUID productId = registerProductViaHttp("셔츠", 10000L, 50);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_SALE"))
                .andExpect(jsonPath("$.soldOut").value(false))
                .andExpect(jsonPath("$.fromPrice").value(10000))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].orderable").value(true));
    }

    @Test
    @DisplayName("없는 상품 상세 조회는 404 PRODUCT_NOT_FOUND로 거부된다")
    void getProductReturns404ForMissingProduct() throws Exception {
        mvc.perform(get("/api/v1/products/{productId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("재고 없는 ACTIVE 변형만 있으면 상세가 품절로 표시된다")
    void getProductShowsSoldOutWhenNoStock() throws Exception {
        UUID productId = registerProductViaHttp("셔츠", 10000L, 0);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldOut").value(true))
                .andExpect(jsonPath("$.variants[0].orderable").value(false));
    }

    @Test
    @DisplayName("상품 목록 조회는 200으로 노출 상품을 대표가·품절·페이지 정보와 함께 싣는다(최신 등록순)")
    void listReturnsExposedProductsWithPagination() throws Exception {
        UUID productId = registerProductViaHttp("목록셔츠", 15000L, 5);

        mvc.perform(get("/api/v1/products").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].fromPrice").value(15000))
                .andExpect(jsonPath("$.products[0].soldOut").value(false))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber());
    }

    @Test
    @DisplayName("재고 없는 상품은 목록에서 품절로 표시된다(파라미터 생략 시 기본 페이지)")
    void listShowsSoldOutProduct() throws Exception {
        UUID productId = registerProductViaHttp("품절모자", 7000L, 0);

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].soldOut").value(true));
    }

    @Test
    @DisplayName("1 미만 page·size는 400 VALIDATION_FAILED로 거부된다")
    void listRejectsInvalidPageParams() throws Exception {
        mvc.perform(get("/api/v1/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        mvc.perform(get("/api/v1/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("숨긴 상품의 공개 상세 조회는 404 PRODUCT_NOT_FOUND로 은닉된다")
    void getProductHidesHiddenProduct() throws Exception {
        UUID productId = registerProductViaHttp("은닉셔츠", 10000L, 5);

        mvc.perform(get("/api/v1/products/{productId}", productId)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/products/{productId}/hide", productId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("카탈로그 목록·상세 조회는 무인증으로 접근된다(공개 표면)")
    void catalogIsPublicWithoutAuthentication() throws Exception {
        UUID productId = registerProductViaHttp("공개셔츠", 10000L, 5);

        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{productId}", productId)).andExpect(status().isOk());
    }

    private UUID registerProductViaHttp(String name, long price, int initialQuantity) throws Exception {
        ProductRegistrationRequest request =
                new ProductRegistrationRequest(name, null, price, List.of(), initialQuantity);
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
}

package com.commerce.api.presentation.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.presentation.v1.request.AddressRequest;
import com.commerce.api.presentation.v1.request.CheckoutRequest;
import com.commerce.api.presentation.v1.request.OptionRequest;
import com.commerce.api.presentation.v1.request.ProductRegistrationRequest;
import com.commerce.api.presentation.v1.request.VariantRegistrationRequest;
import com.commerce.api.presentation.v1.response.ProductRegistrationResponse;
import com.commerce.api.presentation.v1.response.VariantRegistrationResponse;
import com.commerce.cart.service.CartAppender;
import com.commerce.member.service.MemberAppender;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductControllerTest extends WebIntegrationTest {

    private static final String KEY_HEADER = "Idempotency-Key";

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final ProductReader productReader;
    private final ProductVariantReader variantReader;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;

    ProductControllerTest(
            MockMvc mvc,
            ObjectMapper objectMapper,
            ProductReader productReader,
            ProductVariantReader variantReader,
            MemberAppender memberAppender,
            CartAppender cartAppender) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
        this.productReader = productReader;
        this.variantReader = variantReader;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
    }

    @Test
    @DisplayName("상품 등록이 201로 상품 ID를 반환하고 ON_SALE 상품·옵션 변형을 시딩한다")
    void registerReturnsProductId() throws Exception {
        ProductRegistrationRequest request =
                new ProductRegistrationRequest("티셔츠", "면 100%", 10000L, List.of(new OptionRequest("색상", "빨강")), 50);

        String body = mvc.perform(post("/api/v1/products")
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
        ProductRegistrationRequest request = new ProductRegistrationRequest("  ", null, 10000L, List.of(), 50);

        mvc.perform(post("/api/v1/products")
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

        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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

        mvc.perform(get("/api/v1/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].fromPrice").value(15000))
                .andExpect(jsonPath("$.products[0].soldOut").value(false))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
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
    @DisplayName("음수 page·1 미만 size는 400 VALIDATION_FAILED로 거부된다")
    void listRejectsInvalidPageParams() throws Exception {
        mvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        mvc.perform(get("/api/v1/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("추가 변형 등록이 201로 변형 ID를 반환하고 상세에 두 변형이 노출되며 새 변형이 체크아웃된다")
    void addVariantExposesBothVariantsAndChecksOut() throws Exception {
        UUID productId = registerProductViaHttp("옵션셔츠", 10000L, 5);
        UUID variantId = addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(2));

        UUID memberId = memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
        cartAppender.addItem(memberId, variantId, 2);
        CheckoutRequest request = new CheckoutRequest(memberId, addressRequest(), 0L, null, PaymentMethod.CARD);
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비-RETIRED 변형과 같은 옵션 조합의 추가 등록은 409 PRODUCT_VARIANT_DUPLICATE_OPTION으로 거부된다")
    void addVariantRejectsDuplicateOptionCombination() throws Exception {
        UUID productId = registerProductViaHttp("중복셔츠", 10000L, 5);
        addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);

        VariantRegistrationRequest duplicate =
                new VariantRegistrationRequest(13000L, List.of(new OptionRequest("색상", "파랑")), 1);
        mvc.perform(post("/api/v1/products/{productId}/variants", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VARIANT_DUPLICATE_OPTION"));
    }

    @Test
    @DisplayName("은퇴한 옵션 조합은 새 변형으로 재등록된다")
    void addVariantAllowsRetiredCombinationAgain() throws Exception {
        UUID productId = registerProductViaHttp("재등록셔츠", 10000L, 5);
        UUID variantId = addVariantViaHttp(productId, 12000L, List.of(new OptionRequest("색상", "파랑")), 3);
        mvc.perform(post("/api/v1/product-variants/{variantId}/retire", variantId))
                .andExpect(status().isNoContent());

        UUID reRegistered = addVariantViaHttp(productId, 15000L, List.of(new OptionRequest("색상", "파랑")), 2);

        assertThat(reRegistered).isNotEqualTo(variantId);
    }

    @Test
    @DisplayName("없는 상품의 추가 변형 등록은 404 PRODUCT_NOT_FOUND로 거부된다")
    void addVariantReturns404ForMissingProduct() throws Exception {
        VariantRegistrationRequest request = new VariantRegistrationRequest(10000L, List.of(), 1);

        mvc.perform(post("/api/v1/products/{productId}/variants", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("가격 0의 추가 변형 등록은 400 VALIDATION_FAILED로 거부된다")
    void addVariantRejectsNonPositivePrice() throws Exception {
        UUID productId = registerProductViaHttp("검증셔츠", 10000L, 5);

        mvc.perform(post("/api/v1/products/{productId}/variants", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0,\"options\":[],\"initialQuantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private UUID registerProductViaHttp(String name, long price, int initialQuantity) throws Exception {
        ProductRegistrationRequest request =
                new ProductRegistrationRequest(name, null, price, List.of(), initialQuantity);
        String body = mvc.perform(post("/api/v1/products")
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
        String body = mvc.perform(post("/api/v1/products/{productId}/variants", productId)
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

    private static AddressRequest addressRequest() {
        return new AddressRequest("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 409 DUPLICATE_REQUEST로 거부된다")
    void duplicateIdempotencyKeyRejected() throws Exception {
        ProductRegistrationRequest request = new ProductRegistrationRequest("모자", null, 5000L, List.of(), 10);
        String json = objectMapper.writeValueAsString(request);
        String key = "product-" + UUID.randomUUID();

        mvc.perform(post("/api/v1/products")
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/products")
                        .header(KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }
}

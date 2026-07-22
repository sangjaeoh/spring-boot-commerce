package com.commerce.api.web.v1.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.api.web.v1.WebIntegrationTest;
import com.commerce.product.application.CategoryAppender;
import com.commerce.product.application.ProductModifier;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProductControllerTest extends WebIntegrationTest {

    private final MockMvc mvc;
    private final CategoryAppender categoryAppender;
    private final ProductModifier productModifier;

    ProductControllerTest(MockMvc mvc, CategoryAppender categoryAppender, ProductModifier productModifier) {
        this.mvc = mvc;
        this.categoryAppender = categoryAppender;
        this.productModifier = productModifier;
    }

    @Test
    @DisplayName("카테고리 필터가 키워드 검색·가격 정렬과 조합된다")
    void categoryFilterCombinesWithKeywordAndSort() throws Exception {
        UUID categoryId = categoryAppender.create("필터분류-" + UUID.randomUUID());
        UUID otherCategoryId = categoryAppender.create("타분류-" + UUID.randomUUID());
        String token = UUID.randomUUID().toString().substring(0, 8);
        UUID pricyId = seedProduct(token + " 셔츠고가", 30000L, 5, categoryId);
        UUID cheapId = seedProduct(token + " 셔츠저가", 10000L, 5, categoryId);
        seedProduct(token + " 바지", 20000L, 5, otherCategoryId);

        mvc.perform(get("/api/v1/products").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mvc.perform(get("/api/v1/products")
                        .param("categoryId", categoryId.toString())
                        .param("keyword", "셔츠저가"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value(cheapId.toString()));

        mvc.perform(get("/api/v1/products")
                        .param("categoryId", categoryId.toString())
                        .param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(cheapId.toString()))
                .andExpect(jsonPath("$.products[1].id").value(pricyId.toString()));
    }

    @Test
    @DisplayName("카테고리 안 숨김 상품은 필터 결과에서 제외된다")
    void hiddenProductExcludedFromCategoryFilter() throws Exception {
        UUID categoryId = categoryAppender.create("숨김분류-" + UUID.randomUUID());
        UUID productId = seedProduct("숨김분류셔츠", 10000L, 5, categoryId);
        mvc.perform(get("/api/v1/products").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()));

        productModifier.hide(productId);

        mvc.perform(get("/api/v1/products").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0));
    }

    @Test
    @DisplayName("상품 상세 조회는 200으로 ACTIVE 변형·주문가능·대표가를 싣는다")
    void getProductReturnsDetailWithOrderableVariant() throws Exception {
        UUID productId = seedProduct("셔츠", 10000L, 50);

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
        UUID productId = seedProduct("셔츠", 10000L, 0);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldOut").value(true))
                .andExpect(jsonPath("$.variants[0].orderable").value(false));
    }

    @Test
    @DisplayName("상품 목록 조회는 200으로 노출 상품을 대표가·품절·페이지 정보와 함께 싣는다(최신 등록순)")
    void listReturnsExposedProductsWithPagination() throws Exception {
        UUID productId = seedProduct("목록셔츠", 15000L, 5);

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
        UUID productId = seedProduct("품절모자", 7000L, 0);

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                .andExpect(jsonPath("$.products[0].soldOut").value(true));
    }

    @Test
    @DisplayName("키워드 검색이 상품명 부분 일치 상품만 반환한다")
    void listFiltersByKeyword() throws Exception {
        String token = "검색토큰" + UUID.randomUUID().toString().substring(0, 8);
        UUID matchedId = seedProduct(token + " 셔츠", 10000L, 5);
        seedProduct("무관한 바지", 10000L, 5);

        mvc.perform(get("/api/v1/products").param("keyword", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value(matchedId.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("일치 상품이 없는 키워드 검색은 빈 목록을 반환한다")
    void listReturnsEmptyForUnmatchedKeyword() throws Exception {
        mvc.perform(get("/api/v1/products").param("keyword", "없는키워드" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("숨긴 상품은 키워드 검색에서도 제외된다")
    void listKeywordSearchExcludesHiddenProduct() throws Exception {
        String token = "숨김토큰" + UUID.randomUUID().toString().substring(0, 8);
        UUID productId = seedProduct(token + " 셔츠", 10000L, 5);

        productModifier.hide(productId);

        mvc.perform(get("/api/v1/products").param("keyword", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(0));
    }

    @Test
    @DisplayName("가격순 정렬이 대표가 기준으로 정렬하고 기본은 최신 등록순이다")
    void listSortsByPriceAndDefaultsToLatest() throws Exception {
        String token = "정렬토큰" + UUID.randomUUID().toString().substring(0, 8);
        UUID expensiveId = seedProduct(token + " 고가", 30000L, 5);
        UUID cheapId = seedProduct(token + " 저가", 10000L, 5);
        UUID middleId = seedProduct(token + " 중가", 20000L, 5);

        mvc.perform(get("/api/v1/products").param("keyword", token).param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(cheapId.toString()))
                .andExpect(jsonPath("$.products[1].id").value(middleId.toString()))
                .andExpect(jsonPath("$.products[2].id").value(expensiveId.toString()));

        mvc.perform(get("/api/v1/products").param("keyword", token).param("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(expensiveId.toString()))
                .andExpect(jsonPath("$.products[1].id").value(middleId.toString()))
                .andExpect(jsonPath("$.products[2].id").value(cheapId.toString()));

        mvc.perform(get("/api/v1/products").param("keyword", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(middleId.toString()))
                .andExpect(jsonPath("$.products[1].id").value(cheapId.toString()))
                .andExpect(jsonPath("$.products[2].id").value(expensiveId.toString()));
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
        UUID productId = seedProduct("은닉셔츠", 10000L, 5);

        mvc.perform(get("/api/v1/products/{productId}", productId)).andExpect(status().isOk());

        productModifier.hide(productId);

        mvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("카탈로그 목록·상세 조회는 무인증으로 접근된다(공개 표면)")
    void catalogIsPublicWithoutAuthentication() throws Exception {
        UUID productId = seedProduct("공개셔츠", 10000L, 5);

        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{productId}", productId)).andExpect(status().isOk());
    }

    private UUID seedProduct(String name, long price, int initialQuantity) {
        return seedProduct(name, price, initialQuantity, null);
    }

    private UUID seedProduct(
            String name, long price, int initialQuantity, @org.jspecify.annotations.Nullable UUID categoryId) {
        return seedOnSaleProduct(name, null, categoryId, Money.of(price), initialQuantity);
    }
}

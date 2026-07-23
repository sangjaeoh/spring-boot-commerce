package com.commerce.domain.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.product.domain.exception.ProductStatusException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");

    @Test
    @DisplayName("생성 시 HIDDEN이고 삭제시각이 없다")
    void createsHidden() {
        Product product = Product.create("티셔츠", "기본 반팔");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.getName()).isEqualTo("티셔츠");
        assertThat(product.getDescription()).isEqualTo("기본 반팔");
        assertThat(product.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("노출하면 ON_SALE이 된다")
    void showTransitionsToOnSale() {
        Product product = Product.create("티셔츠", null);
        product.show();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 노출된 상품은 다시 노출할 수 없다")
    void cannotShowWhenOnSale() {
        Product product = Product.create("티셔츠", null);
        product.show();
        assertThatThrownBy(product::show).isInstanceOf(ProductStatusException.class);
    }

    @Test
    @DisplayName("숨김 상품은 숨길 수 없다")
    void cannotHideWhenHidden() {
        assertThatThrownBy(Product.create("티셔츠", null)::hide).isInstanceOf(ProductStatusException.class);
    }

    @Test
    @DisplayName("설명을 null로 지울 수 있다")
    void changeDescriptionToNull() {
        Product product = Product.create("티셔츠", "기본 반팔");
        product.changeDescription(null);
        assertThat(product.getDescription()).isNull();
    }

    @Test
    @DisplayName("삭제하면 삭제시각이 기록된다")
    void deleteSetsDeletedAt() {
        Product product = Product.create("티셔츠", null);
        product.delete(NOW);
        assertThat(product.getDeletedAt()).isNotNull();
    }
}

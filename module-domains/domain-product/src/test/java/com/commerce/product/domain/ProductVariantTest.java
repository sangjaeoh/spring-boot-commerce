package com.commerce.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.shared.entity.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductVariantTest {

    private ProductVariant disabledVariant() {
        return ProductVariant.create(UUID.randomUUID(), Money.of(1000L), NormalizedOptions.of(List.of()));
    }

    @Test
    @DisplayName("생성 시 DISABLED이고 가격·옵션이 설정된다")
    void createsDisabled() {
        ProductVariant variant = ProductVariant.create(
                UUID.randomUUID(), Money.of(1000L), NormalizedOptions.of(List.of(new ProductOption("Color", "Red"))));
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.DISABLED);
        assertThat(variant.getPrice()).isEqualTo(Money.of(1000L));
        assertThat(variant.getOptionSignature()).isEqualTo("color:red");
        assertThat(variant.getOptionLabel()).isEqualTo("Red");
    }

    @Test
    @DisplayName("가격이 1 미만이면 생성할 수 없다")
    void rejectsPriceBelowOne() {
        assertThatThrownBy(() -> ProductVariant.create(UUID.randomUUID(), Money.ZERO, NormalizedOptions.of(List.of())))
                .isInstanceOf(InvalidVariantException.class);
    }

    @Test
    @DisplayName("활성/비활성을 오간다")
    void enableAndDisable() {
        ProductVariant variant = disabledVariant();
        variant.enable();
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.ACTIVE);
        variant.disable();
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.DISABLED);
    }

    @Test
    @DisplayName("이미 활성인 변형은 다시 활성화할 수 없다")
    void cannotEnableWhenActive() {
        ProductVariant variant = disabledVariant();
        variant.enable();
        assertThatThrownBy(variant::enable).isInstanceOf(ProductVariantStatusException.class);
    }

    @Test
    @DisplayName("가격을 바꿀 수 있다")
    void changePrice() {
        ProductVariant variant = disabledVariant();
        variant.changePrice(Money.of(2000L));
        assertThat(variant.getPrice()).isEqualTo(Money.of(2000L));
    }

    @Test
    @DisplayName("은퇴는 종료 상태라 이후 어떤 전이·가격 변경도 거부한다")
    void retiredIsTerminal() {
        ProductVariant variant = disabledVariant();
        variant.retire();
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.RETIRED);
        assertThatThrownBy(variant::enable).isInstanceOf(ProductVariantStatusException.class);
        assertThatThrownBy(variant::disable).isInstanceOf(ProductVariantStatusException.class);
        assertThatThrownBy(variant::retire).isInstanceOf(ProductVariantStatusException.class);
        assertThatThrownBy(() -> variant.changePrice(Money.of(2000L)))
                .isInstanceOf(ProductVariantStatusException.class);
    }
}

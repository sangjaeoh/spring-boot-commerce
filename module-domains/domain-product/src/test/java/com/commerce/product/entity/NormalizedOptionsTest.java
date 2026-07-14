package com.commerce.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.product.exception.InvalidVariantException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizedOptionsTest {

    @Test
    @DisplayName("옵션이 없으면 빈 시그니처와 null 라벨")
    void emptyWhenNoOptions() {
        NormalizedOptions normalized = NormalizedOptions.of(List.of());
        assertThat(normalized.signature()).isEmpty();
        assertThat(normalized.label()).isNull();
    }

    @Test
    @DisplayName("시그니처는 옵션명으로 정렬하고 라벨은 입력 순서를 보존한다")
    void signatureSortsByNameLabelKeepsInputOrder() {
        NormalizedOptions normalized =
                NormalizedOptions.of(List.of(new ProductOption("Size", "L"), new ProductOption("Color", "Red")));
        assertThat(normalized.signature()).isEqualTo("color:red;size:l");
        assertThat(normalized.label()).isEqualTo("L / Red");
    }

    @Test
    @DisplayName("시그니처는 케이스 폴딩하고 라벨은 대소문자를 보존한다")
    void signatureCaseFoldsLabelPreservesCase() {
        NormalizedOptions normalized = NormalizedOptions.of(List.of(new ProductOption("Color", "RED")));
        assertThat(normalized.signature()).isEqualTo("color:red");
        assertThat(normalized.label()).isEqualTo("RED");
    }

    @Test
    @DisplayName("옵션명·값의 앞뒤 공백을 제거한다")
    void trimsOptionNameAndValue() {
        NormalizedOptions normalized = NormalizedOptions.of(List.of(new ProductOption("  Color  ", "  Red  ")));
        assertThat(normalized.signature()).isEqualTo("color:red");
        assertThat(normalized.label()).isEqualTo("Red");
    }

    @Test
    @DisplayName("옵션명이 대소문자만 다르면 중복으로 거부한다")
    void rejectsDuplicateOptionNameCaseInsensitively() {
        assertThatThrownBy(() -> NormalizedOptions.of(
                        List.of(new ProductOption("Color", "Red"), new ProductOption("color", "Blue"))))
                .isInstanceOf(InvalidVariantException.class);
    }

    @Test
    @DisplayName("옵션명이나 값이 비면 거부한다")
    void rejectsBlankNameOrValue() {
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("  ", "Red"))))
                .isInstanceOf(InvalidVariantException.class);
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("Color", ""))))
                .isInstanceOf(InvalidVariantException.class);
    }

    @Test
    @DisplayName("구분자 문자를 포함하면 거부한다")
    void rejectsSeparatorCharacters() {
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("Co:lor", "Red"))))
                .isInstanceOf(InvalidVariantException.class);
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("Color", "Re;d"))))
                .isInstanceOf(InvalidVariantException.class);
    }
}

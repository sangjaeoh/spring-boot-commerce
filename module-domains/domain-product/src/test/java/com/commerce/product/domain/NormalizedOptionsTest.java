package com.commerce.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.product.domain.exception.InvalidVariantException;
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
    @DisplayName("NBSP(U+00A0)로 감싼 값은 NFKC 후 strip돼 시그니처에 잔류하지 않는다")
    void stripsNbspRevealedByNormalizationFromSignature() {
        // NBSP는 Character.isWhitespace가 아니라 strip 단독으로는 안 지워지고, NFKC가 일반 공백(U+0020)으로 접은 뒤 strip돼야 제거된다
        NormalizedOptions padded =
                NormalizedOptions.of(List.of(new ProductOption("\u00A0Color\u00A0", "\u00A0Red\u00A0")));
        NormalizedOptions clean = NormalizedOptions.of(List.of(new ProductOption("Color", "Red")));
        assertThat(padded.signature()).isEqualTo("color:red");
        assertThat(padded.signature()).isEqualTo(clean.signature());
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

    @Test
    @DisplayName("정규화(NFKC) 후 구분자가 되는 전각 문자도 거부한다")
    void rejectsSeparatorRevealedByNormalization() {
        // 전각 콜론 U+FF1A(：)·세미콜론 U+FF1B(；)은 NFKC로 ASCII ':'·';'로 접혀 시그니처를 오염시킨다
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("Co：lor", "Red"))))
                .isInstanceOf(InvalidVariantException.class);
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("Color", "Re；d"))))
                .isInstanceOf(InvalidVariantException.class);
    }

    @Test
    @DisplayName("전각 구분자 우회로 서로 다른 옵션 조합이 같은 시그니처로 충돌하지 않는다")
    void distinctOptionsDoNotCollideViaFullwidthSeparators() {
        NormalizedOptions two = NormalizedOptions.of(List.of(new ProductOption("a", "1"), new ProductOption("b", "2")));
        assertThat(two.signature()).isEqualTo("a:1;b:2");
        // 한 옵션에 전각 구분자를 심어 "a:1;b:2"를 위조하려는 입력은 거부돼 충돌 자체가 성립하지 않는다
        assertThatThrownBy(() -> NormalizedOptions.of(List.of(new ProductOption("a", "1；b：2"))))
                .isInstanceOf(InvalidVariantException.class);
    }
}

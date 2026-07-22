package com.commerce.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    @DisplayName("올바른 형식은 생성된다")
    void acceptsValidFormat() {
        assertThat(Email.of("user@example.com").value()).isEqualTo("user@example.com");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "plainaddress",
                "no-at-sign.com",
                "@no-local.com",
                "no-domain@",
                "has space@example.com",
                "no@dot"
            })
    @DisplayName("잘못된 형식은 예외")
    void rejectsInvalidFormat(String invalid) {
        assertThatThrownBy(() -> Email.of(invalid)).isInstanceOf(InvalidEmailException.class);
    }
}

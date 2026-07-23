package com.commerce.common.core.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    @DisplayName("버전7 변형2 UUID를 생성한다")
    void generatesVersion7Variant2() {
        UUID uuid = UuidV7Generator.generate();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("생성값은 유일하다")
    void generatesUniqueValues() {
        Set<UUID> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(UuidV7Generator.generate());
        }
        assertThat(generated).hasSize(1000);
    }
}

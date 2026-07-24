package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.type.TypeFactory;

class CacheRegistrationTest {

    @Test
    void holdsNameTtlAndValueType() {
        var valueType = TypeFactory.createDefaultInstance().constructType(String.class);

        var registration = new CacheRegistration("product:category:v1", Duration.ofMinutes(10), valueType);

        assertThat(registration.name()).isEqualTo("product:category:v1");
        assertThat(registration.ttl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(registration.valueType()).isEqualTo(valueType);
    }
}

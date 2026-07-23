package com.commerce.domain.shared.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/** {@link Money}를 단일 {@code BIGINT} 컬럼으로 매핑하는 JPA 변환기다. */
@Converter
public class MoneyConverter implements AttributeConverter<Money, Long> {

    @Override
    public @Nullable Long convertToDatabaseColumn(@Nullable Money attribute) {
        return attribute == null ? null : attribute.amount();
    }

    @Override
    public @Nullable Money convertToEntityAttribute(@Nullable Long dbData) {
        return dbData == null ? null : Money.of(dbData);
    }
}

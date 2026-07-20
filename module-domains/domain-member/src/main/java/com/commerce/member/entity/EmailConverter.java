package com.commerce.member.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/** {@link Email}을 단일 {@code VARCHAR} 컬럼으로 매핑하는 컨버터다. */
@Converter
public class EmailConverter implements AttributeConverter<Email, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable Email convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : Email.of(dbData);
    }
}

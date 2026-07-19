package com.commerce.product.entity;

import com.commerce.product.exception.InvalidVariantException;
import com.commerce.product.exception.ProductErrorCode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 변형 옵션을 평탄화한 값 객체다.
 *
 * @param signature 정규 옵션 시그니처. 옵션이 없으면 {@code ""}
 * @param label 표시 라벨. 옵션이 없으면 {@code null}
 */
public record NormalizedOptions(String signature, @Nullable String label) {

    private static final String PAIR_SEPARATOR = ";";
    private static final String NAME_VALUE_SEPARATOR = ":";
    private static final String LABEL_SEPARATOR = " / ";

    public NormalizedOptions {
        if (signature.isEmpty() != (label == null)) {
            throw new IllegalArgumentException("옵션 시그니처와 라벨의 존재가 일치하지 않는다");
        }
    }

    /**
     * 옵션 목록을 정규화한다. 옵션이 없으면 빈 시그니처를 반환한다.
     *
     * @throws InvalidVariantException 옵션명·값이 비었거나, 구분자 문자를 포함하거나, 옵션명이 중복일 때
     */
    public static NormalizedOptions of(List<ProductOption> options) {
        if (options.isEmpty()) {
            return new NormalizedOptions("", null);
        }
        TreeMap<String, String> canonicalByName = new TreeMap<>();
        StringBuilder label = new StringBuilder();
        for (ProductOption option : options) {
            String rawValue = option.value().trim();
            String name = caseFold(option.name());
            String value = caseFold(option.value());
            if (name.isEmpty() || value.isEmpty() || hasSeparator(name) || hasSeparator(value)) {
                throw new InvalidVariantException(ProductErrorCode.INVALID_OPTION);
            }
            if (canonicalByName.putIfAbsent(name, value) != null) {
                throw new InvalidVariantException(ProductErrorCode.INVALID_OPTION);
            }
            label.append(label.isEmpty() ? "" : LABEL_SEPARATOR).append(rawValue);
        }
        String signature = canonicalByName.entrySet().stream()
                .map(entry -> entry.getKey() + NAME_VALUE_SEPARATOR + entry.getValue())
                .collect(Collectors.joining(PAIR_SEPARATOR));
        return new NormalizedOptions(signature, label.toString());
    }

    /** 값에 구분자 문자가 섞였는지 판정한다. */
    private static boolean hasSeparator(String value) {
        return value.contains(PAIR_SEPARATOR) || value.contains(NAME_VALUE_SEPARATOR);
    }

    /** 유니코드 정규화·공백 제거·소문자화로 값을 대조용 형태로 접는다. */
    private static String caseFold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }
}

package com.commerce.api.presentation.v1.request;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 상품명·상세 설명 편집 요청이다. 설명이 null이면 설명을 비운다. */
public record ProductEditRequest(
        @NotBlank String name, @Nullable String description) {}

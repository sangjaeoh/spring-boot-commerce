package com.commerce.web.support;

import jakarta.validation.constraints.NotBlank;

/** Bean Validation 승격 검증용 요청 DTO. */
public record TestRequest(@NotBlank String name) {}

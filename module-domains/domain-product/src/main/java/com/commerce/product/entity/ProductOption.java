package com.commerce.product.entity;

/**
 * 변형 생성 입력의 옵션 하나(옵션명·값)다.
 *
 * <p>정규화·검증은 {@link NormalizedOptions}가 소유한다.
 */
public record ProductOption(String name, String value) {}

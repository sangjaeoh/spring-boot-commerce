package com.commerce.product.entity;

/** 상품 변형(판매·재고 단위)의 카탈로그 상태다. {@code RETIRED}는 재전이 없는 종료 상태다. */
public enum ProductVariantStatus {
    ACTIVE,
    DISABLED,
    RETIRED
}

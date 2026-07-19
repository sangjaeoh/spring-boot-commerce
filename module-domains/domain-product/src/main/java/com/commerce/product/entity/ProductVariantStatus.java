package com.commerce.product.entity;

/** 상품 변형의 카탈로그 상태다. */
public enum ProductVariantStatus {
    /** 판매 제공. 비활성 상태에서만 진입한다. */
    ACTIVE,
    /** 카탈로그 제공 중단. 변형 생성 직후 진입하고 판매 제공을 중단하면 되돌아온다. */
    DISABLED,
    /** 은퇴. 판매 제공·비활성 양쪽에서 진입하는 종료 상태. */
    RETIRED
}

package com.commerce.product.entity;

/** 상품 카탈로그 그룹의 노출 상태다. */
public enum ProductStatus {
    /** 판매중. 숨김 상태에서 노출 전환하면 진입한다. */
    ON_SALE,
    /** 숨김. 상품 등록 직후 진입하고 노출을 거두면 되돌아온다. */
    HIDDEN
}

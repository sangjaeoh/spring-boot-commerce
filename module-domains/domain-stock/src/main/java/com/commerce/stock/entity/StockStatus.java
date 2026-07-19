package com.commerce.stock.entity;

/** 재고의 판매 가능 상태다. */
public enum StockStatus {
    /** 판매 가능. 재고 생성 직후와 수동 품절 해제 시 진입한다. */
    SELLABLE,
    /** 수동 품절. 판매 가능 상태에서만 진입한다. */
    SOLD_OUT,
    /** 단종. 판매 가능·수동 품절 양쪽에서 진입하는 종료 상태. */
    DISCONTINUED
}

package com.commerce.stock.entity;

/** 재고의 판매 가능 상태다. 소진(quantity=0)은 이 축이 아니라 수량에서 파생한다. */
public enum StockStatus {
    SELLABLE,
    SOLD_OUT,
    DISCONTINUED
}

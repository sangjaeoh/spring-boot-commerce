package com.commerce.api.facade;

/** 카탈로그 목록 정렬 기준이다. */
public enum ProductSort {
    /** 최신 등록순. */
    LATEST,
    /** 대표가(ACTIVE 변형 최저가) 낮은순. */
    PRICE_ASC,
    /** 대표가(ACTIVE 변형 최저가) 높은순. */
    PRICE_DESC
}

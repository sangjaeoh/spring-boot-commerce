package com.commerce.domain.product.application.required;

/** 상품 이미지 바이트 보관 포트다. URL 형태는 구현이 소유한다. */
public interface ImageStore {

    /** 이미지를 키 아래 보관하고 공개 URL을 반환한다. */
    String store(String key, byte[] content, String contentType);

    /** 보관된 이미지를 지운다. 없는 키면 아무 일도 하지 않는다. */
    void remove(String key);
}

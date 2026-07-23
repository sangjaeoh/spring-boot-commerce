package com.commerce.domain.product.application.provided;

import java.util.UUID;

/** 카테고리 생성을 담당하는 서비스다. */
public interface CategoryAppender {

    /** 카테고리를 생성하고 새 카테고리 ID를 반환한다. */
    UUID create(String name);
}

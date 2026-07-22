package com.commerce.product.info;

import com.commerce.product.entity.Category;
import java.util.UUID;

/** 카테고리 조회 경계 모델이다. */
public record CategoryInfo(UUID id, String name) {

    /** 카테고리 엔티티에서 조회 모델을 만든다. */
    public static CategoryInfo from(Category category) {
        return new CategoryInfo(category.getId(), category.getName());
    }
}

package com.commerce.product.application.provided;

import com.commerce.product.application.info.CategoryInfo;
import java.util.List;

/** 카테고리 조회를 담당하는 서비스다. */
public interface CategoryReader {

    /** 활성 카테고리 목록을 이름순으로 조회한다. 없으면 빈 목록이다. */
    List<CategoryInfo> getCategories();
}

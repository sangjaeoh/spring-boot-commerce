package com.commerce.app.admin.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryAppender;
import com.commerce.domain.product.application.provided.CategoryModifier;
import com.commerce.domain.product.application.provided.CategoryReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/** 카테고리 캐시(product:category:v1) 무효화 대표 시나리오를 검증한다. */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CategoryCacheInvalidationTest extends FacadeIntegrationTest {

    private final CategoryReader categoryReader;
    private final CategoryAppender categoryAppender;
    private final CategoryModifier categoryModifier;

    CategoryCacheInvalidationTest(
            CategoryReader categoryReader, CategoryAppender categoryAppender, CategoryModifier categoryModifier) {
        this.categoryReader = categoryReader;
        this.categoryAppender = categoryAppender;
        this.categoryModifier = categoryModifier;
    }

    @Test
    @DisplayName("카테고리 이름 변경 커밋 후 카테고리 목록 조회는 캐시된 옛 이름이 아니라 변경된 이름을 즉시 반환한다")
    void renameEvictsCacheAndReflectsNewNameImmediately() {
        UUID categoryId = categoryAppender.create("가전-옛이름-" + UUID.randomUUID());
        List<CategoryInfo> beforeRename = categoryReader.getCategories();
        assertThat(beforeRename).anyMatch(category -> category.id().equals(categoryId));

        String newName = "가전-새이름-" + UUID.randomUUID();
        categoryModifier.rename(categoryId, newName);

        List<CategoryInfo> afterRename = categoryReader.getCategories();
        assertThat(afterRename)
                .filteredOn(category -> category.id().equals(categoryId))
                .extracting(CategoryInfo::name)
                .containsExactly(newName);
    }
}

package com.commerce.domain.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryReader;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * category의 영속 이음새를 실 PostgreSQL로 검증한다.
 *
 * <p>이 슬라이스는 {@code @EnableCaching}이 없어 {@code @Cacheable}이 순수 메타데이터로만 존재한다 —
 * {@link DefaultCategoryReader}의 캐시 분리(미스 경로 위임)가 읽기 동작을 바꾸지 않음을 검증한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/product",
            "spring.flyway.schemas=product",
            "spring.flyway.default-schema=product"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DefaultCategoryReader.class, TransactionalCategoryReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CategoryPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final CategoryReader categoryReader;
    private final CategoryRepository categoryRepository;

    CategoryPersistenceTest(CategoryReader categoryReader, CategoryRepository categoryRepository) {
        this.categoryReader = categoryReader;
        this.categoryRepository = categoryRepository;
    }

    @Test
    @DisplayName("활성 카테고리를 이름순으로 조회하고 삭제분은 제외한다 — validate 스키마 정합")
    void getCategoriesOrderedByNameExcludingDeleted() {
        categoryRepository.save(Category.create("의류"));
        categoryRepository.save(Category.create("가전"));
        Category deleted = categoryRepository.save(Category.create("삭제예정"));
        deleted.delete(java.time.Instant.now());
        categoryRepository.save(deleted);

        List<CategoryInfo> categories = categoryReader.getCategories();

        assertThat(categories).extracting(CategoryInfo::name).containsExactly("가전", "의류");
    }
}

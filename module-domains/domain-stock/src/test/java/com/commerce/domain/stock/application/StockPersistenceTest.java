package com.commerce.domain.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.stock.application.info.StockInfo;
import com.commerce.domain.stock.application.provided.StockAppender;
import com.commerce.domain.stock.application.provided.StockModifier;
import com.commerce.domain.stock.application.provided.StockReader;
import com.commerce.domain.stock.domain.Stock;
import com.commerce.domain.stock.domain.StockStatus;
import com.commerce.domain.stock.domain.exception.DuplicateStockException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * stock 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code ddl-auto=validate} 정합, variant_id 유니크, 낙관락({@code @Version}) 증가를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/stock",
            "spring.flyway.schemas=stock",
            "spring.flyway.default-schema=stock"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DefaultStockAppender.class, DefaultStockModifier.class, DefaultStockReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StockPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final StockAppender stockAppender;
    private final StockModifier stockModifier;
    private final StockReader stockReader;
    private final TestEntityManager em;

    StockPersistenceTest(
            StockAppender stockAppender, StockModifier stockModifier, StockReader stockReader, TestEntityManager em) {
        this.stockAppender = stockAppender;
        this.stockModifier = stockModifier;
        this.stockReader = stockReader;
        this.em = em;
    }

    @Test
    @DisplayName("생성 후 조회 왕복 — validate 스키마 정합")
    void createThenRead() {
        UUID variantId = UUID.randomUUID();
        stockAppender.create(variantId, 10);
        em.flush();
        em.clear();

        StockInfo info = stockReader.getByVariantId(variantId);

        assertThat(info.variantId()).isEqualTo(variantId);
        assertThat(info.quantity()).isEqualTo(10);
        assertThat(info.status()).isEqualTo(StockStatus.SELLABLE);
    }

    @Test
    @DisplayName("변형당 재고는 하나뿐이라 중복 생성은 거부된다")
    void duplicateVariantRejected() {
        UUID variantId = UUID.randomUUID();
        stockAppender.create(variantId, 10);
        em.flush();

        assertThatThrownBy(() -> stockAppender.create(variantId, 5)).isInstanceOf(DuplicateStockException.class);
    }

    @Test
    @DisplayName("차감이 반영되고 낙관락 버전이 증가한다")
    void deductPersistsAndIncrementsVersion() {
        UUID variantId = UUID.randomUUID();
        UUID stockId = stockAppender.create(variantId, 10);
        em.flush();
        em.clear();
        assertThat(Objects.requireNonNull(em.find(Stock.class, stockId)).getVersion())
                .isZero();

        stockModifier.deduct(variantId, 3);
        em.flush();
        em.clear();

        Stock reloaded = Objects.requireNonNull(em.find(Stock.class, stockId));
        assertThat(reloaded.getQuantity()).isEqualTo(7);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }
}

package com.commerce.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.cart.info.CartInfo;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartModifier;
import com.commerce.cart.service.CartReader;
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
 * cart 도메인의 영속 이음새를 실 PostgreSQL로 검증한다.
 *
 * <p>{@code ddl-auto=validate} 정합, get-or-create·캐스케이드 저장, 같은 변형 합산, orphanRemoval 제거를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/cart",
            "spring.flyway.schemas=cart",
            "spring.flyway.default-schema=cart"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({CartAppender.class, CartModifier.class, CartReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final CartAppender cartAppender;
    private final CartModifier cartModifier;
    private final CartReader cartReader;
    private final TestEntityManager em;

    CartPersistenceTest(
            CartAppender cartAppender, CartModifier cartModifier, CartReader cartReader, TestEntityManager em) {
        this.cartAppender = cartAppender;
        this.cartModifier = cartModifier;
        this.cartReader = cartReader;
        this.em = em;
    }

    @Test
    @DisplayName("담기가 장바구니를 만들고 라인을 저장한다 — validate 스키마 정합")
    void addItemCreatesCartAndPersists() {
        UUID memberId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        cartAppender.addItem(memberId, variantId, 2);
        em.flush();
        em.clear();

        CartInfo cart = cartReader.getCart(memberId);

        assertThat(cart.items()).hasSize(1);
        CartItemInfo item = cart.items().get(0);
        assertThat(item.variantId()).isEqualTo(variantId);
        assertThat(item.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 변형 재담기는 새 라인 없이 수량을 합산한다")
    void sameVariantMergesPersisted() {
        UUID memberId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        cartAppender.addItem(memberId, variantId, 2);
        cartAppender.addItem(memberId, variantId, 3);
        em.flush();
        em.clear();

        CartInfo cart = cartReader.getCart(memberId);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("라인 제거는 자식 행을 삭제한다(orphanRemoval)")
    void removeItemDeletesChildRow() {
        UUID memberId = UUID.randomUUID();
        UUID keep = UUID.randomUUID();
        UUID drop = UUID.randomUUID();
        cartAppender.addItem(memberId, keep, 1);
        cartAppender.addItem(memberId, drop, 1);
        em.flush();
        cartModifier.removeItem(memberId, drop);
        em.flush();
        em.clear();

        CartInfo cart = cartReader.getCart(memberId);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).variantId()).isEqualTo(keep);
    }

    @Test
    @DisplayName("장바구니가 없으면 빈 장바구니를 반환한다")
    void emptyCartWhenNone() {
        CartInfo cart = cartReader.getCart(UUID.randomUUID());
        assertThat(cart.items()).isEmpty();
    }
}

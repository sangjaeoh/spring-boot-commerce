package com.commerce.domain.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.common.jpa.config.JpaAuditingConfig;
import com.commerce.domain.cart.application.provided.CartAppender;
import com.commerce.domain.cart.application.provided.CartReader;
import com.commerce.event.order.OrderPaid;
import java.util.Set;
import java.util.UUID;
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

/** {@link OrderPaid} 소비자의 장바구니 비우기 행동을 실 PostgreSQL 영속 슬라이스로 검증하는 테스트다. */
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
@Import({
    OrderPaidListener.class,
    DefaultCartModifier.class,
    DefaultCartAppender.class,
    DefaultCartReader.class,
    JpaAuditingConfig.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderPaidListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final OrderPaidListener listener;
    private final CartAppender cartAppender;
    private final CartReader cartReader;

    OrderPaidListenerTest(OrderPaidListener listener, CartAppender cartAppender, CartReader cartReader) {
        this.listener = listener;
        this.cartAppender = cartAppender;
        this.cartReader = cartReader;
    }

    @Test
    @DisplayName("OrderPaid를 소비해 주문된 변형 라인만 장바구니에서 제거한다")
    void consumeRemovesOrderedVariantLines() {
        UUID memberId = UUID.randomUUID();
        UUID orderedVariantId = UUID.randomUUID();
        UUID remainingVariantId = UUID.randomUUID();
        cartAppender.addItem(memberId, orderedVariantId, 1);
        cartAppender.addItem(memberId, remainingVariantId, 2);

        listener.on(new OrderPaid(UUID.randomUUID(), memberId, Set.of(orderedVariantId)));

        assertThat(cartReader.getCart(memberId).items())
                .singleElement()
                .satisfies(item -> assertThat(item.variantId()).isEqualTo(remainingVariantId));
    }

    @Test
    @DisplayName("중복 이벤트 소비는 1회 효과로 멱등하다")
    void duplicateConsumptionIsIdempotent() {
        UUID memberId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        cartAppender.addItem(memberId, variantId, 1);
        OrderPaid event = new OrderPaid(UUID.randomUUID(), memberId, Set.of(variantId));

        listener.on(event);
        listener.on(event);

        assertThat(cartReader.getCart(memberId).items()).isEmpty();
    }
}

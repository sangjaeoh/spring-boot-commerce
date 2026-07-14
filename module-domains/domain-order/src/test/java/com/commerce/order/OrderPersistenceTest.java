package com.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.core.money.Money;
import com.commerce.messaging.publish.MessagePublisher;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.FulfillmentStatus;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderModifier;
import com.commerce.order.service.OrderReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * order 도메인의 영속 이음새를 실 PostgreSQL로 검증한다.
 *
 * <p>두 테이블 {@code ddl-auto=validate} 정합, Address 임베디드·Money 컬럼 왕복, 라인 캐스케이드, 이행 전이를 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/ordering",
            "spring.flyway.schemas=ordering",
            "spring.flyway.default-schema=ordering"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({OrderAppender.class, OrderReader.class, OrderModifier.class, OrderPersistenceTest.NoOpMessagingConfig.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final TestEntityManager em;

    OrderPersistenceTest(
            OrderAppender orderAppender, OrderReader orderReader, OrderModifier orderModifier, TestEntityManager em) {
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.em = em;
    }

    private UUID place() {
        List<OrderLineSnapshot> lines = List.of(
                new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(10000L), 2));
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(UUID.randomUUID(), lines, address, Money.ZERO, Money.of(3000L), null);
    }

    @Test
    @DisplayName("주문 생성 후 조회 왕복 — Address 임베디드·라인 캐스케이드·validate 스키마 정합")
    void placeThenGetOrder() {
        UUID orderId = place();
        em.flush();
        em.clear();

        OrderInfo order = orderReader.getOrder(orderId);

        assertThat(order.totalAmount()).isEqualTo(Money.of(20000L));
        assertThat(order.payAmount()).isEqualTo(Money.of(23000L));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.NOT_STARTED);
        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().get(0).optionLabel()).isEqualTo("Red / L");
        assertThat(order.shippingAddress().recipientName()).isEqualTo("홍길동");
        assertThat(order.orderNumber()).isNotBlank();
    }

    @Test
    @DisplayName("결제·출고·배송 완료 이행이 반영된다")
    void fulfillmentFlowPersists() {
        UUID orderId = place();
        em.flush();
        orderModifier.markPaid(orderId);
        em.flush();
        orderModifier.ship(orderId);
        em.flush();
        orderModifier.confirmDelivery(orderId);
        em.flush();
        em.clear();

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @TestConfiguration
    static class NoOpMessagingConfig {

        @Bean
        MessagePublisher messagePublisher() {
            return event -> {};
        }
    }
}

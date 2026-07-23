package com.commerce.domain.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.order.application.required.FulfillmentRepository;
import com.commerce.domain.order.domain.FulfillmentStatus;
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

/** {@link OrderPaid} 소비자의 이행 생성 행동을 실 PostgreSQL 영속 슬라이스로 검증하는 테스트다. */
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
@Import(FulfillmentPreparationListener.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FulfillmentPreparationListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final FulfillmentPreparationListener listener;
    private final FulfillmentRepository fulfillmentRepository;

    FulfillmentPreparationListenerTest(
            FulfillmentPreparationListener listener, FulfillmentRepository fulfillmentRepository) {
        this.listener = listener;
        this.fulfillmentRepository = fulfillmentRepository;
    }

    @Test
    @DisplayName("OrderPaid를 소비해 이행을 PREPARING으로 생성한다")
    void consumeCreatesPreparingFulfillment() {
        UUID orderId = UUID.randomUUID();

        listener.on(new OrderPaid(orderId, UUID.randomUUID(), Set.of(UUID.randomUUID())));

        assertThat(fulfillmentRepository.findByOrderId(orderId))
                .isPresent()
                .get()
                .satisfies(fulfillment -> assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING));
    }

    @Test
    @DisplayName("중복 이벤트 소비는 1회 생성 효과로 멱등하다")
    void duplicateConsumptionIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        OrderPaid event = new OrderPaid(orderId, UUID.randomUUID(), Set.of(UUID.randomUUID()));

        listener.on(event);
        listener.on(event);

        assertThat(fulfillmentRepository.findByOrderId(orderId)).isPresent();
        assertThat(fulfillmentRepository.count()).isEqualTo(1);
    }
}

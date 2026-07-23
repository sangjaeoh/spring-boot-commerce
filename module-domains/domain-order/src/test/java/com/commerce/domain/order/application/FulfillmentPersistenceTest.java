package com.commerce.domain.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.common.event.publish.MessagePublisher;
import com.commerce.domain.order.application.info.OrderInfo;
import com.commerce.domain.order.application.provided.OrderAppender;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.FulfillmentStatus;
import com.commerce.domain.order.domain.OrderLineSnapshot;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import com.commerce.domain.shared.entity.Money;
import com.commerce.event.order.OrderPaid;
import java.util.List;
import java.util.Set;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 이행 애그리거트의 생성 조율·전이·조회 합성을 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code OrderPaid} 소비는 아웃박스 릴레이 경유가 아니라 리스너를 직접 호출해 시뮬레이션한다.
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
@Import({
    DefaultOrderAppender.class,
    DefaultOrderReader.class,
    DefaultOrderModifier.class,
    FulfillmentPreparationListener.class,
    FulfillmentPersistenceTest.NoOpMessagingConfig.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FulfillmentPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final OrderAppender orderAppender;
    private final OrderReader orderReader;
    private final OrderModifier orderModifier;
    private final FulfillmentPreparationListener fulfillmentPreparationListener;
    private final TestEntityManager em;

    FulfillmentPersistenceTest(
            OrderAppender orderAppender,
            OrderReader orderReader,
            OrderModifier orderModifier,
            FulfillmentPreparationListener fulfillmentPreparationListener,
            TestEntityManager em) {
        this.orderAppender = orderAppender;
        this.orderReader = orderReader;
        this.orderModifier = orderModifier;
        this.fulfillmentPreparationListener = fulfillmentPreparationListener;
        this.em = em;
    }

    private UUID place() {
        List<OrderLineSnapshot> lines = List.of(
                new OrderLineSnapshot(UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(10000L), 2));
        Address address = Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
        return orderAppender.place(UUID.randomUUID(), lines, address, Money.ZERO, Money.of(3000L), null);
    }

    /** 결제 완료하고 이행 생성 리스너를 직접 호출해 PREPARING 이행을 만든다. */
    private UUID paidOrderWithFulfillment() {
        UUID orderId = place();
        em.flush();
        orderModifier.markPaid(orderId);
        em.flush();
        OrderInfo order = orderReader.getOrder(orderId);
        fulfillmentPreparationListener.on(new OrderPaid(orderId, order.memberId(), Set.of()));
        em.flush();
        return orderId;
    }

    @Test
    @DisplayName("결제·출고·배송 완료 이행이 반영되고 운송장 기록이 왕복한다")
    void fulfillmentFlowPersists() {
        UUID orderId = paidOrderWithFulfillment();

        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        em.flush();
        orderModifier.confirmDelivery(orderId);
        em.flush();
        em.clear();

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.carrier()).isEqualTo("CJ대한통운");
        assertThat(order.trackingNumber()).isEqualTo("688900123456");
    }

    @Test
    @DisplayName("결제 완료 전 주문의 출고는 거부된다")
    void shipRejectsUnpaidOrder() {
        UUID orderId = place();
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.NOT_PAID));
    }

    @Test
    @DisplayName("결제는 완료됐으나 이행 생성이 아직 반영되지 않은 주문의 출고는 거부된다")
    void shipRejectsBeforeFulfillmentCreated() {
        // FulfillmentPreparationListener를 의도적으로 호출하지 않아 markPaid 커밋과 이행 생성 사이 레이스 창을 재현한다.
        UUID orderId = place();
        em.flush();
        orderModifier.markPaid(orderId);
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.FULFILLMENT_NOT_READY));
    }

    @Test
    @DisplayName("라인 취소 진행 중인 주문의 출고는 거부된다")
    void shipRejectsWhileLineCancellationInProgress() {
        UUID orderId = paidOrderWithFulfillment();
        OrderInfo order = orderReader.getOrder(orderId);
        UUID lineId = order.lines().get(0).id();
        orderModifier.beginLineCancellation(orderId, lineId);
        em.flush();

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
    }

    @TestConfiguration
    static class NoOpMessagingConfig {

        @Bean
        MessagePublisher messagePublisher() {
            return event -> {};
        }
    }
}

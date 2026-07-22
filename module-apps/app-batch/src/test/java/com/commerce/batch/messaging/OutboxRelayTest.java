package com.commerce.batch.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.batch.BatchIntegrationTest;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.messaging.publish.MessagePublisher;
import com.commerce.order.event.OrderPaid;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 아웃박스의 원자성(커밋과 발행의 동시 확정)과 릴레이 전달·멱등 소비를 검증하는 테스트다.
 *
 * <p>발행은 {@link MessagePublisher} 포트로(도메인 발행 경로와 동일), 소비 관측은 장바구니 비우기
 * 리스너의 효과로 한다. 스케줄 릴레이가 배경에서 함께 돌 수 있으므로 단언은 어느 실행이 소비했든
 * 성립하는 상태 기반으로 한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OutboxRelayTest extends BatchIntegrationTest {

    private final MessagePublisher messagePublisher;
    private final OutboxRelay outboxRelay;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CartReader cartReader;
    private final ProductVariantReader variantReader;

    OutboxRelayTest(
            MessagePublisher messagePublisher,
            OutboxRelay outboxRelay,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CartReader cartReader,
            ProductVariantReader variantReader) {
        this.messagePublisher = messagePublisher;
        this.outboxRelay = outboxRelay;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.cartReader = cartReader;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 아웃박스에 행이 남지 않고 릴레이를 돌려도 소비가 없다")
    void rollbackLeavesNoOutboxRowAndNoConsumption() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartAppender.addItem(memberId, variantId, 1);
        OrderPaid event = new OrderPaid(UUID.randomUUID(), memberId, Set.of(variantId));

        transactionTemplate.executeWithoutResult(status -> {
            messagePublisher.publish(event);
            status.setRollbackOnly();
        });

        assertThat(outboxCount(event.orderId())).isZero();
        outboxRelay.relay();
        assertThat(cartReader.getCart(memberId).items()).hasSize(1);
    }

    @Test
    @DisplayName("커밋된 이벤트는 릴레이가 재발행·소비해 장바구니가 비고 행이 발행 표시된다")
    void committedEventIsRelayedAndConsumed() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartAppender.addItem(memberId, variantId, 1);
        OrderPaid event = new OrderPaid(UUID.randomUUID(), memberId, Set.of(variantId));

        transactionTemplate.executeWithoutResult(status -> messagePublisher.publish(event));

        assertThat(outboxCount(event.orderId())).isEqualTo(1);
        outboxRelay.relay();

        assertThat(cartReader.getCart(memberId).items()).isEmpty();
        assertThat(unpublishedCount(event.orderId())).isZero();
    }

    @Test
    @DisplayName("발행 표시 전 크래시로 재전달돼도 중복 부작용 없이 같은 상태로 수렴한다")
    void redeliveryAfterMarkFailureIsIdempotent() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartAppender.addItem(memberId, variantId, 1);
        OrderPaid event = new OrderPaid(UUID.randomUUID(), memberId, Set.of(variantId));
        transactionTemplate.executeWithoutResult(status -> messagePublisher.publish(event));
        outboxRelay.relay();
        assertThat(cartReader.getCart(memberId).items()).isEmpty();

        // 발행 후·표시 전 크래시가 남기는 상태(소비됨·미표시)를 재현한다 — 다음 릴레이가 재전달한다.
        jdbcTemplate.update(
                "UPDATE messaging.outbox SET published_at = NULL WHERE payload LIKE ?", orderIdPattern(event));
        outboxRelay.relay();

        assertThat(cartReader.getCart(memberId).items()).isEmpty();
        assertThat(unpublishedCount(event.orderId())).isZero();
    }

    @Test
    @DisplayName("활성 트랜잭션 없는 발행은 거부되고 행이 남지 않는다")
    void publishOutsideTransactionIsRejected() {
        OrderPaid event = new OrderPaid(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()));

        assertThatThrownBy(() -> messagePublisher.publish(event)).isInstanceOf(IllegalStateException.class);

        assertThat(outboxCount(event.orderId())).isZero();
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedVariant() {
        UUID productId = seedOnSaleProduct(Money.of(10000L), 10);
        return variantReader.getByProductId(productId).get(0).id();
    }

    /** 이벤트 페이로드의 주문 ID로 이 테스트가 남긴 아웃박스 행만 센다(스위트 공유 DB 격리). */
    private long outboxCount(UUID orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messaging.outbox WHERE payload LIKE ?", Long.class, "%" + orderId + "%");
        return count == null ? 0 : count;
    }

    private long unpublishedCount(UUID orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messaging.outbox WHERE published_at IS NULL AND payload LIKE ?",
                Long.class,
                "%" + orderId + "%");
        return count == null ? 0 : count;
    }

    private String orderIdPattern(OrderPaid event) {
        return "%" + event.orderId() + "%";
    }
}

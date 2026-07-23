package com.commerce.app.batch.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.commerce.app.batch.BatchIntegrationTest;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.order.application.provided.OrderAppender;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.application.provided.OrderReader;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.OrderLineSnapshot;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.payment.application.provided.PaymentAppender;
import com.commerce.domain.payment.application.provided.PaymentProcessor;
import com.commerce.domain.payment.domain.FailureReason;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.product.application.info.ProductVariantInfo;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockModifier;
import com.commerce.domain.stock.application.provided.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 스윕·리컨실 보상의 재고 복원이 일시 낙관락 충돌(동시 체크아웃 경합)을 라인 단위 재시도로 흡수해, 취소
 * 전이 커밋 후 남은 라인의 복원이 유실되지 않음을 결함 주입으로 검증하는 테스트다.
 *
 * <p>충돌은 {@code StockModifier.restore}에 주입한다 — 실 경합의 재현이 아니라 재시도 경로의 특성화다.
 * 재시도 상한(3회) 안의 연속 충돌 2회를 주입해 상한 경계까지 확인한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CompensationRestoreRetryTest extends BatchIntegrationTest {

    @MockitoSpyBean
    private StockModifier stockModifier;

    private final PendingOrderSweepFacade pendingOrderSweepFacade;
    private final MemberAppender memberAppender;
    private final OrderAppender orderAppender;
    private final OrderModifier orderModifier;
    private final OrderReader orderReader;
    private final StockReader stockReader;
    private final PaymentAppender paymentAppender;
    private final PaymentProcessor paymentProcessor;
    private final ProductVariantReader variantReader;
    private final JdbcTemplate jdbcTemplate;

    CompensationRestoreRetryTest(
            PendingOrderSweepFacade pendingOrderSweepFacade,
            MemberAppender memberAppender,
            OrderAppender orderAppender,
            OrderModifier orderModifier,
            OrderReader orderReader,
            StockReader stockReader,
            PaymentAppender paymentAppender,
            PaymentProcessor paymentProcessor,
            ProductVariantReader variantReader,
            JdbcTemplate jdbcTemplate) {
        this.pendingOrderSweepFacade = pendingOrderSweepFacade;
        this.memberAppender = memberAppender;
        this.orderAppender = orderAppender;
        this.orderModifier = orderModifier;
        this.orderReader = orderReader;
        this.stockReader = stockReader;
        this.paymentAppender = paymentAppender;
        this.paymentProcessor = paymentProcessor;
        this.variantReader = variantReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("스윕 직접 보상의 라인 복원이 일시 충돌을 재시도로 흡수해 다중 라인 전부가 결국 복원된다")
    void sweepRestoreAbsorbsTransientConflictAndRestoresAllLines() {
        UUID memberId = registerMember();
        UUID firstVariantId = seedProduct(Money.of(10000L), 50);
        UUID secondVariantId = seedProduct(Money.of(20000L), 40);
        UUID orderId = orderAppender.place(
                memberId,
                List.of(snapshot(firstVariantId, 2), snapshot(secondVariantId, 3)),
                address(),
                Money.ZERO,
                Money.ZERO,
                null);
        stockModifier.deduct(firstVariantId, 2);
        stockModifier.deduct(secondVariantId, 3);
        orderModifier.markStockDeducted(orderId);
        injectTransientConflicts(secondVariantId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(firstVariantId).quantity()).isEqualTo(50);
        assertThat(stockReader.getByVariantId(secondVariantId).quantity()).isEqualTo(40);
    }

    @Test
    @DisplayName("리컨실 보상(FAILED × PENDING 위임)의 라인 복원도 일시 충돌을 재시도로 흡수해 결국 복원된다")
    void delegatedCompensationRestoreAbsorbsTransientConflict() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot(variantId, 2)), address(), Money.ZERO, Money.ZERO, null);
        stockModifier.deduct(variantId, 2);
        orderModifier.markStockDeducted(orderId);
        UUID paymentId = paymentAppender.request(orderId, Money.of(20000L), PaymentMethod.CARD);
        paymentProcessor.confirmFailure(paymentId, FailureReason.INSUFFICIENT_BALANCE);
        agePastPaymentStaleAfter(paymentId);
        injectTransientConflicts(variantId);

        pendingOrderSweepFacade.reconcile(Instant.now());

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    /** 해당 변형의 restore 첫 두 시도에 낙관락 충돌을 주입한다 — 재시도 상한(3회) 경계까지 태운다. */
    private void injectTransientConflicts(UUID variantId) {
        doThrow(new ObjectOptimisticLockingFailureException("stock", variantId))
                .doThrow(new ObjectOptimisticLockingFailureException("stock", variantId))
                .doCallRealMethod()
                .when(stockModifier)
                .restore(eq(variantId), anyInt());
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = seedOnSaleProduct(price, quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private OrderLineSnapshot snapshot(UUID variantId, int quantity) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        return new OrderLineSnapshot(
                variant.id(), variant.productId(), "상품", variant.optionLabel(), variant.price(), quantity);
    }

    /** 결제 생성 시각을 확정 경로 유예({@code payment.reconciliation.stale-after}) 이전으로 되돌린다. */
    private void agePastPaymentStaleAfter(UUID paymentId) {
        jdbcTemplate.update(
                "UPDATE payment.payment SET created_at = created_at - INTERVAL '1 hour' WHERE id = ?", paymentId);
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

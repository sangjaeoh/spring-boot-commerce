package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.application.provided.CartAppender;
import com.commerce.cart.application.provided.CartModifier;
import com.commerce.coupon.application.info.IssuedCouponInfo;
import com.commerce.coupon.application.provided.CouponAppender;
import com.commerce.coupon.application.provided.IssuedCouponAppender;
import com.commerce.coupon.application.provided.IssuedCouponModifier;
import com.commerce.coupon.application.provided.IssuedCouponReader;
import com.commerce.coupon.domain.CouponErrorCode;
import com.commerce.coupon.domain.CouponStatusException;
import com.commerce.coupon.domain.Discount;
import com.commerce.coupon.domain.IssuedCouponStatus;
import com.commerce.coupon.domain.ValidityPeriod;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.order.application.info.OrderInfo;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.FulfillmentStatus;
import com.commerce.order.domain.FulfillmentStatusException;
import com.commerce.order.domain.OrderErrorCode;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.domain.OrderStatusException;
import com.commerce.payment.application.info.PaymentInfo;
import com.commerce.payment.application.provided.PaymentReader;
import com.commerce.payment.application.required.PaymentGateway;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.payment.domain.PaymentStatus;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockModifier;
import com.commerce.stock.application.provided.StockReader;
import com.commerce.stock.domain.StockErrorCode;
import com.commerce.stock.domain.StockShortageException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 완료 주문의 취소·복원과 그 재시도 멱등, 동시 중복 취소의 낙관락 직렬화를 통합 검증하는 테스트다.
 *
 * <p>롤백 없는 통합 하네스를 상속해 각 도메인 서비스가 실제로 커밋하므로, 한 메서드 안의 파사드 재시도가 시도
 * 간 실 영속 상태를 본다. 다운스트림 실패는 spy에 doThrow로 1회 주입하고 재시도 직전 {@link Mockito#reset}으로
 * 실제 위임으로 되돌린다. PaymentGateway를 spy로 감싸 환불이 두 시도에 걸쳐 정확히 한 번만 일어나는지 관측한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderCancellationFacadeTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    @MockitoSpyBean
    private StockModifier stockModifier;

    @MockitoSpyBean
    private IssuedCouponModifier issuedCouponModifier;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private final OrderCancellationFacade orderCancellationFacade;
    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CartModifier cartModifier;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final OrderReader orderReader;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;
    private final PaymentReader paymentReader;

    OrderCancellationFacadeTest(
            OrderCancellationFacade orderCancellationFacade,
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CartModifier cartModifier,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            OrderReader orderReader,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader,
            PaymentReader paymentReader) {
        this.orderCancellationFacade = orderCancellationFacade;
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.cartModifier = cartModifier;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.orderReader = orderReader;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
        this.paymentReader = paymentReader;
    }

    @Test
    @DisplayName("결제완료 주문 취소가 주문을 취소하고 재고·쿠폰을 복원한다")
    void cancelRestoresStockAndCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        orderCancellationFacade.cancel(orderId, memberId);

        OrderInfo order = orderReader.getOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("출고된 주문 취소는 거부되고 재고를 복원하지 않는다")
    void cancelRejectsShippedOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");

        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.ORDER_NOT_CANCELLABLE));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(49);
    }

    @Test
    @DisplayName("취소 개시 마커가 커밋된 주문의 출고는 거부된다")
    void shipRejectedAfterCancellationInitiated() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        orderModifier.requestCancellation(orderId);

        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
        assertThat(orderReader.getOrder(orderId).fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
    }

    @Test
    @DisplayName("환불 커밋 후 주문 전이 직전에 끼어든 출고가 거부되어 환불 고아가 남지 않는다")
    void interleavedShipAfterRefundIsRejectedLeavingNoOrphan() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        // 환불 고아 인터리빙을 결정론적으로 재현한다: 취소 가드 통과 → PG 환불·결제 CANCELLED 커밋 → 주문
        // 취소 전이 직전에 출고가 선점을 시도. 마커가 환불 앞에 커밋돼 있어 출고는 거부되고 취소가 완결된다.
        // 출고 시도는 별도 스레드(자기 트랜잭션)로 내고 완료까지 기다린다 — 호출 스레드에서 내면 진행 중
        // 취소 흐름의 트랜잭션에 합류할 수 있어 실경합(독립 커밋 경쟁) 형상이 되지 않는다.
        AtomicReference<Throwable> shipAttempt = new AtomicReference<>();
        doAnswer(invocation -> {
                    Thread shipper = new Thread(() -> shipAttempt.set(
                            catchThrowable(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))));
                    shipper.start();
                    shipper.join();
                    return invocation.callRealMethod();
                })
                .when(orderModifier)
                .cancel(eq(orderId), any());

        orderCancellationFacade.cancel(orderId, memberId);

        assertThat(shipAttempt.get())
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderReader.getOrder(orderId).fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("마커 커밋 후 환불 전 중단 잔여는 출고가 차단된 채 재시도가 취소를 완결한다")
    void markerResidueBeforeRefundBlocksShipUntilRetryCompletes() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        // 마커 커밋 직후·PG 환불 전 중단을 재현한다 — 돈은 아직 움직이지 않았다.
        doThrow(new IllegalStateException("PG 환불 전 중단 주입")).when(paymentGateway).cancel(any(), any());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.APPROVED);

        // 잔여 동안 출고는 거부된다 — 환불이 재개돼도 출고 선점이 고아를 만들 수 없다.
        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));

        // 재시도가 마커를 관용(no-op)하고 환불·취소·복원을 완결한다.
        Mockito.reset(paymentGateway);
        orderCancellationFacade.cancel(orderId, memberId);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("환불 커밋 후 주문 전이 실패 잔여(CANCELLED 결제 × PAID 주문)는 출고가 차단된 채 재시도가 완결한다")
    void refundCommittedResidueBlocksShipUntilRetryCompletes() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        doThrow(new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION))
                .when(orderModifier)
                .cancel(eq(orderId), any());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(OrderStatusException.class);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        Mockito.reset(orderModifier);

        // 환불 커밋과 주문 취소 전이 사이의 창에서도 마커가 출고를 거부한다.
        assertThatThrownBy(() -> orderModifier.ship(orderId, "CJ대한통운", "688900123456"))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));

        orderCancellationFacade.cancel(orderId, memberId);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("취소와 출고가 동시에 겹쳐도 환불 고아(결제 CANCELLED × 주문 SHIPPED)는 어떤 인터리빙에서도 남지 않는다")
    void concurrentCancelAndShipNeverLeaveRefundedShippedOrphan() throws InterruptedException {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);

        // 두 스레드를 취소 개시(마커 커밋)·출고 전이 직전에 정렬해 같은 주문 버전을 두고 경합하는 최악
        // 인터리빙을 강제한다. 진 쪽은 낙관락 충돌 또는 상태 가드로 거부된다.
        CyclicBarrier raceGate = new CyclicBarrier(2);
        doAnswer(invocation -> {
                    awaitQuietly(raceGate);
                    return invocation.callRealMethod();
                })
                .when(orderModifier)
                .requestCancellation(orderId);
        doAnswer(invocation -> {
                    awaitQuietly(raceGate);
                    return invocation.callRealMethod();
                })
                .when(orderModifier)
                .ship(eq(orderId), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                start.await();
                orderCancellationFacade.cancel(orderId, memberId);
            } catch (RuntimeException raceLoss) {
                // 출고가 선점하면 취소는 환불 전(마커 커밋)에 거부·충돌로 끝난다.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        executor.execute(() -> {
            try {
                start.await();
                orderModifier.ship(orderId, "CJ대한통운", "688900123456");
            } catch (RuntimeException raceLoss) {
                // 취소 개시가 선점하면 출고는 마커·충돌로 거부된다.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // 어느 인터리빙이든 돈과 상품이 함께 나가지 않는다 — 출고가 이기면 환불·복원이 없고, 취소가 이기면
        // 출고 없이 환불·복원이 완결된다.
        OrderInfo order = orderReader.getOrder(orderId);
        PaymentInfo payment = paymentReader.getByOrderId(orderId);
        if (order.fulfillmentStatus() == FulfillmentStatus.SHIPPED) {
            assertThat(order.status()).isEqualTo(OrderStatus.PAID);
            assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        } else {
            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        }
    }

    @Test
    @DisplayName("주문 취소 실패로 환불만 커밋된 뒤 재시도가 이미 취소된 결제를 관용해 복원을 완결한다")
    void retryToleratesCancelledPaymentWhenOrderCancelFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new OrderStatusException(OrderErrorCode.INVALID_ORDER_STATE_TRANSITION))
                .when(orderModifier)
                .cancel(eq(orderId), any());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(OrderStatusException.class);
        Mockito.reset(orderModifier);

        orderCancellationFacade.cancel(orderId, memberId);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("동시 취소 2건이 겹쳐도 전이는 한쪽만 커밋되고 재고·쿠폰 복원은 정확히 한 번이다")
    void concurrentDuplicateCancelRestoresExactlyOnce() throws InterruptedException {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);

        // 두 스레드를 주문 취소 전이 직전에 정렬해, 둘 다 PAID를 읽고 가드를 통과한 뒤 쓰는 최악 인터리빙을
        // 강제한다. 한쪽이 결제 취소 낙관락 경합에서 먼저 탈락하면 남은 쪽만 도달하므로 짧게 기다리고 단독
        // 진행한다.
        CyclicBarrier transitionGate = new CyclicBarrier(2);
        doAnswer(invocation -> {
                    try {
                        transitionGate.await(2, TimeUnit.SECONDS);
                    } catch (TimeoutException | BrokenBarrierException raceAlreadyDecided) {
                        // 상대 스레드가 전이에 도달하지 못했다 — 단독 진행.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return invocation.callRealMethod();
                })
                .when(orderModifier)
                .cancel(eq(orderId), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            executor.execute(() -> {
                try {
                    start.await();
                    orderCancellationFacade.cancel(orderId, memberId);
                    succeeded.incrementAndGet();
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // 어느 인터리빙이든 취소는 완결되고 복원은 정확히 한 번이다 — 이중 복원이면 재고가 54로 증식한다.
        // 진 쪽은 낙관락 충돌·상태 가드로 복원 없이 실패하거나, 완결 후 도착해 관용 통과한다.
        assertThat(succeeded.get()).isBetween(1, 2);
        assertThat(succeeded.get() + failed.get()).isEqualTo(2);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    @Test
    @DisplayName("취소 완결 후 재호출은 재고·쿠폰을 추가로 건드리지 않는다")
    void duplicateCancelDoesNotTouchStockOrCouponAgain() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
        orderCancellationFacade.cancel(orderId, memberId);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);

        orderCancellationFacade.cancel(orderId, memberId);

        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("취소 완결 후 쿠폰이 다른 주문에 재사용됐으면 재호출이 그 쿠폰을 풀지 않는다")
    void duplicateCancelDoesNotReloadCouponUsedByAnotherOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID cancelledOrderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
        orderCancellationFacade.cancel(cancelledOrderId, memberId);
        // 주문된 라인의 장바구니 제거는 아웃박스 릴레이(app-batch) 소유가 됐다 — 재주문 픽스처가 직접 비운다.
        cartModifier.removeItems(memberId, Set.of(variantId));
        cartAppender.addItem(memberId, variantId, 2);
        UUID reuseOrderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        orderCancellationFacade.cancel(cancelledOrderId, memberId);

        IssuedCouponInfo coupon = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(coupon.status()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.orderId()).isEqualTo(reuseOrderId);
        assertThat(orderReader.getOrder(reuseOrderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(48);
    }

    @Test
    @DisplayName("재고 복원 실패 후 재호출은 이미 취소된 주문을 복원 재실행 없이 통과시킨다")
    void retryDoesNotRerunRestoreWhenStockRestoreFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new StockShortageException(StockErrorCode.STOCK_SHORTAGE))
                .when(stockModifier)
                .restore(eq(variantId), anyInt());
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(StockShortageException.class);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        Mockito.reset(stockModifier);

        orderCancellationFacade.cancel(orderId, memberId);

        // 취소 커밋과 복원 사이 중단의 복원 유실은 수용된 잔여 한계다 — 재호출이 가산 복원을 재실행하지 않는다.
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("쿠폰 복원 실패 후 재호출은 이미 취소된 주문을 복원 재실행 없이 통과시킨다")
    void retryDoesNotRerunRestoreWhenCouponRestoreFails() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);

        doThrow(new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_USABLE))
                .when(issuedCouponModifier)
                .restoreUse(eq(issuedId), any(UUID.class));
        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(CouponStatusException.class);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        Mockito.reset(issuedCouponModifier);

        orderCancellationFacade.cancel(orderId, memberId);

        // 취소 커밋과 복원 사이 중단의 복원 유실은 수용된 잔여 한계다 — 재호출이 복원을 재실행하지 않는다.
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.USED);
        verify(paymentGateway, times(1)).cancel(any(), any());
    }

    @Test
    @DisplayName("PG 환불이 실패하면 주문은 PAID로 남고 재고·쿠폰을 복원하지 않는다")
    void pgRefundFailureKeepsOrderPaidWithoutRestore() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 4);
        UUID couponId =
                couponAppender.create("정액 1000", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
        doThrow(new IllegalStateException("PG 환불 실패 주입")).when(paymentGateway).cancel(any(), any());

        assertThatThrownBy(() -> orderCancellationFacade.cancel(orderId, memberId))
                .isInstanceOf(IllegalStateException.class);

        // 환불이 복원의 선행조건이다 — 주문은 PAID로 남고 재고·쿠폰을 건드리지 않는다.
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentReader.getByOrderId(orderId).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(46);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.USED);

        // 장애가 걷힌 재시도가 취소를 완결한다.
        Mockito.reset(paymentGateway);
        orderCancellationFacade.cancel(orderId, memberId);
        assertThat(orderReader.getOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    /** 두 스레드를 경합 지점에 정렬한다. 상대가 도달하지 못하면(경합이 이미 결판) 짧게 기다리고 단독 진행한다. */
    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (TimeoutException | BrokenBarrierException raceAlreadyDecided) {
            // 상대 스레드가 정렬 지점에 도달하지 못했다 — 단독 진행.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = seedOnSaleProduct("상품", null, price, quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

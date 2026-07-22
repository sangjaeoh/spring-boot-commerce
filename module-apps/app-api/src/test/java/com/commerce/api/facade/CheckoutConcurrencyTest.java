package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.cart.service.CartAppender;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.info.IssuedCouponInfo;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.OrderStatus;
import com.commerce.order.info.OrderInfo;
import com.commerce.order.service.OrderReader;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;

/**
 * "오버셀 없음" 비기능 요구를 재고 낙관락 경합으로 검증하는 테스트다.
 *
 * <p>재고 M에 동시 체크아웃 N(&gt;M)을 태워, 성공 주문 수가 M을 넘지 않고 최종 재고가 정확히 {@code M − 성공}
 * (0 이상)으로 보존됨을 확인한다. 실패 체크아웃은 주문 생성 전 사전 재고 가드에서 걸려 주문이 남지 않거나(0건),
 * 주문 생성 후 재고 차감(낙관락 충돌·수량 가드)에서 걸려 주문이 CANCELLED로 종결되며, 어느 쪽이든 실린 쿠폰은
 * 소진되지 않는다. 재시도가 없어 성공 수는 M 이하로만 보장되므로(정확히 M을 단정하지 않는다) 경합 편차에 무관하게
 * 반복 통과한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutConcurrencyTest extends FacadeIntegrationTest {

    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final IssuedCouponReader issuedCouponReader;
    private final OrderReader orderReader;
    private final StockReader stockReader;
    private final ProductVariantReader variantReader;

    CheckoutConcurrencyTest(
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            IssuedCouponReader issuedCouponReader,
            OrderReader orderReader,
            StockReader stockReader,
            ProductVariantReader variantReader) {
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.issuedCouponReader = issuedCouponReader;
        this.orderReader = orderReader;
        this.stockReader = stockReader;
        this.variantReader = variantReader;
    }

    @Test
    @DisplayName("재고 M·동시 체크아웃 N(>M) 경합에서 오버셀 없이 성공은 M 이하이고 실패 주문은 CANCELLED·쿠폰 미소진으로 종결된다")
    void concurrentCheckoutNeverOversells() throws InterruptedException {
        int stock = 5;
        int concurrency = 12;
        UUID variantId = seedProduct(stock);
        // 무제한 발급 정책 — 쿠폰 한도는 경합시키지 않고 재고만 유일한 경합 자원으로 둔다.
        UUID couponId =
                couponAppender.create("정액 할인", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        List<Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            UUID memberId = registerMember();
            cartAppender.addItem(memberId, variantId, 1);
            buyers.add(new Buyer(memberId, issuedCouponAppender.issue(couponId, memberId)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (Buyer buyer : buyers) {
            executor.execute(() -> {
                try {
                    start.await();
                    checkoutFacade.checkout(
                            buyer.memberId(), address(), Money.ZERO, buyer.issuedCouponId(), PaymentMethod.CARD);
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

        // 오버셀 없음: 성공은 재고 이하, 최종 재고는 정확히 M − 성공(음수 불가).
        assertThat(succeeded.get()).isBetween(1, stock);
        assertThat(failed.get()).isEqualTo(concurrency - succeeded.get());
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(stock - succeeded.get());

        // 모든 시도가 종결: 성공은 PAID 주문 1건·쿠폰 USED. 실패는 사전 재고 가드에서 걸려 주문이 없거나(0건)
        // 차감 경합에서 취소된 CANCELLED 주문 1건이며, 어느 쪽이든 쿠폰은 ISSUED로 미소진이다. 체크아웃 1회당
        // 주문은 최대 1건이다.
        int paid = 0;
        int failedOut = 0;
        for (Buyer buyer : buyers) {
            List<OrderInfo> orders = ordersOf(buyer.memberId());
            assertThat(orders.size()).isLessThanOrEqualTo(1);
            IssuedCouponStatus couponStatus = issuedCouponReader
                    .getIssuedCoupon(buyer.issuedCouponId(), buyer.memberId())
                    .status();
            if (orders.size() == 1 && orders.get(0).status() == OrderStatus.PAID) {
                assertThat(couponStatus).isEqualTo(IssuedCouponStatus.USED);
                paid++;
            } else {
                if (!orders.isEmpty()) {
                    assertThat(orders.get(0).status()).isEqualTo(OrderStatus.CANCELLED);
                }
                assertThat(couponStatus).isEqualTo(IssuedCouponStatus.ISSUED);
                failedOut++;
            }
        }
        assertThat(paid).isEqualTo(succeeded.get());
        assertThat(failedOut).isEqualTo(failed.get());
    }

    @Test
    @DisplayName("같은 쿠폰을 실은 동시 두 체크아웃은 정확히 한 주문만 할인과 함께 완결된다(쿠폰 이중 사용 차단)")
    void concurrentCheckoutsCannotDoubleUseSameCoupon() throws InterruptedException {
        UUID variantId = seedProduct(50);
        UUID memberId = registerMember();
        cartAppender.addItem(memberId, variantId, 1);
        UUID couponId =
                couponAppender.create("정액 할인", Discount.fixed(Money.of(1000L)), Money.ZERO, validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            executor.execute(() -> {
                try {
                    start.await();
                    checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD);
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

        // IssuedCoupon 낙관락이 유일 방어다 — 어느 인터리빙이든 한쪽만 USED 전이에 성공하고, 진 쪽은 사전
        // 검증 거부(주문 없음) 또는 쿠폰 확정 실패 보상(CANCELLED)으로 끝난다.
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(1);
        List<OrderInfo> orders = ordersOf(memberId);
        List<OrderInfo> paidOrders =
                orders.stream().filter(o -> o.status() == OrderStatus.PAID).toList();
        assertThat(paidOrders).hasSize(1);
        OrderInfo paid = paidOrders.get(0);
        assertThat(paid.discountAmount()).isEqualTo(Money.of(1000L));
        orders.stream()
                .filter(o -> !o.id().equals(paid.id()))
                .forEach(o -> assertThat(o.status()).isEqualTo(OrderStatus.CANCELLED));
        // 쿠폰은 이긴 주문에만 걸려 있다 — 진 쪽 보상의 restoreUse는 주문 불일치 no-op이라 재장전되지 않는다.
        IssuedCouponInfo coupon = issuedCouponReader.getIssuedCoupon(issuedId, memberId);
        assertThat(coupon.status()).isEqualTo(IssuedCouponStatus.USED);
        assertThat(coupon.orderId()).isEqualTo(paid.id());
        // 진 쪽 차감은 복원돼 재고는 이긴 주문 1건만 반영한다.
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(49);
    }

    private List<OrderInfo> ordersOf(UUID memberId) {
        return orderReader.getOrdersByMember(memberId, PageRequest.of(0, 10)).getContent();
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = seedOnSaleProduct("상품", null, Money.of(10000L), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }

    private record Buyer(UUID memberId, UUID issuedCouponId) {}
}

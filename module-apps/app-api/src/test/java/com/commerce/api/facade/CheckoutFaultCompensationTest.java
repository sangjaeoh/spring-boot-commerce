package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.commerce.cart.application.provided.CartAppender;
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
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.application.provided.OrderReader;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.CancellationReason;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockModifier;
import com.commerce.stock.application.provided.StockReader;
import com.commerce.stock.domain.StockErrorCode;
import com.commerce.stock.domain.StockShortageException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 체크아웃 사가의 중간 실패(재고 차감·쿠폰 확정) 보상 분기를 결함 주입으로 검증하는 테스트다.
 *
 * <p>이 두 분기는 낙관락·경합으로만 트리거돼 자연 재현이 어려우므로 도메인 서비스를 spy로 감싸 던지게 한다.
 * 취소 호출과 취소 사유, 복원 결과, 차감 완료 마커의 기록 시점(전 라인 차감 뒤 ∧ 쿠폰 확정 앞)을 확인한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutFaultCompensationTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private StockModifier stockModifier;

    @MockitoSpyBean
    private IssuedCouponModifier issuedCouponModifier;

    @MockitoSpyBean
    private OrderModifier orderModifier;

    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;
    private final OrderReader orderReader;

    CheckoutFaultCompensationTest(
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader,
            OrderReader orderReader) {
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
        this.orderReader = orderReader;
    }

    @Test
    @DisplayName("재고 차감 실패 시 주문을 STOCK_SHORTAGE로 취소한다")
    void deductFailureCancelsOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        doThrow(new StockShortageException(StockErrorCode.STOCK_SHORTAGE))
                .when(stockModifier)
                .deduct(eq(variantId), anyInt());

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOf(StockShortageException.class);

        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(orderModifier).cancel(orderIdCaptor.capture(), eq(CancellationReason.STOCK_SHORTAGE));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        // 차감 전·중 실패에는 차감 완료 마커가 기록되지 않는다 — 마커 존재 ⇒ 전 라인 차감 완료의 한 방향.
        assertThat(orderReader.getOrder(orderIdCaptor.getValue()).stockDeductedAt())
                .isNull();
    }

    @Test
    @DisplayName("쿠폰 확정 실패 시 재고를 복원하고 주문을 COUPON_CONFLICT로 취소하며 쿠폰은 ISSUED로 남는다")
    void couponUseFailureRestoresStockAndCancelsOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        doThrow(new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_USABLE))
                .when(issuedCouponModifier)
                .use(eq(issuedId), eq(memberId), any(UUID.class));

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD))
                .isInstanceOf(CouponStatusException.class);

        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(orderModifier).cancel(orderIdCaptor.capture(), eq(CancellationReason.COUPON_CONFLICT));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        // 쿠폰 확정에 도달했다면 차감 완료 마커가 이미 기록돼 있다 — 마커가 전 라인 차감 뒤·쿠폰 확정 앞임을 고정.
        assertThat(orderReader.getOrder(orderIdCaptor.getValue()).stockDeductedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("다중 라인 차감이 중간에 실패하면 차감된 라인만 정확히 복원되고 어떤 라인도 증식·유출되지 않는다")
    void multiLineDeductFailureRestoresExactly() {
        UUID memberId = registerMember();
        UUID firstVariantId = seedProduct(50);
        UUID secondVariantId = seedProduct(40);
        cartAppender.addItem(memberId, firstVariantId, 2);
        cartAppender.addItem(memberId, secondVariantId, 3);
        doThrow(new StockShortageException(StockErrorCode.STOCK_SHORTAGE))
                .when(stockModifier)
                .deduct(eq(secondVariantId), anyInt());

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOf(StockShortageException.class);

        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(orderModifier).cancel(orderIdCaptor.capture(), eq(CancellationReason.STOCK_SHORTAGE));
        // 라인 순서(실패 라인이 첫 번째든 두 번째든)와 무관하게, deducted 리스트 정밀도로 차감된 라인만
        // 복원돼 두 라인 모두 원값이다 — 증식(과복원)도 유출(미복원)도 없다.
        assertThat(stockReader.getByVariantId(firstVariantId).quantity()).isEqualTo(50);
        assertThat(stockReader.getByVariantId(secondVariantId).quantity()).isEqualTo(40);
        assertThat(orderReader.getOrder(orderIdCaptor.getValue()).stockDeductedAt())
                .isNull();
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
}

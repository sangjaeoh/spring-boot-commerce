package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.commerce.cart.service.CartAppender;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.exception.CouponErrorCode;
import com.commerce.coupon.exception.CouponStatusException;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.service.OrderModifier;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.exception.StockErrorCode;
import com.commerce.stock.exception.StockShortageException;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 체크아웃 사가의 중간 실패(재고 차감·쿠폰 확정) 보상 분기를 결함 주입으로 검증한다.
 *
 * <p>이 두 분기는 낙관락·경합으로만 트리거돼 자연 재현이 어려우므로 도메인 서비스를 spy로 감싸 던지게 한다.
 * 취소 호출과 취소 사유, 복원 결과를 확인한다.
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
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final ProductVariantReader variantReader;

    CheckoutFaultCompensationTest(
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            ProductVariantReader variantReader) {
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.variantReader = variantReader;
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

        verify(orderModifier).cancel(any(UUID.class), eq(CancellationReason.STOCK_SHORTAGE));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("쿠폰 확정 실패 시 재고를 복원하고 주문을 COUPON_CONFLICT로 취소하며 쿠폰은 ISSUED로 남는다")
    void couponUseFailureRestoresStockAndCancelsOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);
        doThrow(new CouponStatusException(CouponErrorCode.ISSUED_COUPON_NOT_USABLE))
                .when(issuedCouponModifier)
                .use(eq(issuedId), any(UUID.class));

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD))
                .isInstanceOf(CouponStatusException.class);

        verify(orderModifier).cancel(any(UUID.class), eq(CancellationReason.COUPON_CONFLICT));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

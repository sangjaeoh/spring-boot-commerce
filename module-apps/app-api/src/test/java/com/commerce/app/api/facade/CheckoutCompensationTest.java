package com.commerce.app.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.commerce.app.api.exception.ApiErrorCode;
import com.commerce.app.api.exception.ApiException;
import com.commerce.domain.cart.application.provided.CartAppender;
import com.commerce.domain.cart.application.provided.CartReader;
import com.commerce.domain.coupon.application.provided.CouponAppender;
import com.commerce.domain.coupon.application.provided.IssuedCouponAppender;
import com.commerce.domain.coupon.application.provided.IssuedCouponReader;
import com.commerce.domain.coupon.domain.Discount;
import com.commerce.domain.coupon.domain.IssuedCouponStatus;
import com.commerce.domain.coupon.domain.ValidityPeriod;
import com.commerce.domain.member.application.provided.MemberAppender;
import com.commerce.domain.order.application.provided.OrderModifier;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.CancellationReason;
import com.commerce.domain.payment.application.provided.PaymentReader;
import com.commerce.domain.payment.domain.FailureReason;
import com.commerce.domain.payment.domain.PaymentMethod;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.stock.application.provided.StockReader;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 실패 경로를 프로덕션 fake PG의 거절 트리거 금액(끝 세 자리 999)으로 태워 동기 보상을 검증하는 테스트다.
 * 테스트 더블이 아니라 실제 조립되는 어댑터 위에서 보상이 동작함을 확인한다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CheckoutCompensationTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final CartReader cartReader;
    private final StockReader stockReader;
    private final ProductVariantReader variantReader;
    private final IssuedCouponReader issuedCouponReader;
    private final PaymentReader paymentReader;

    CheckoutCompensationTest(
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            CartReader cartReader,
            StockReader stockReader,
            ProductVariantReader variantReader,
            IssuedCouponReader issuedCouponReader,
            PaymentReader paymentReader) {
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.cartReader = cartReader;
        this.stockReader = stockReader;
        this.variantReader = variantReader;
        this.issuedCouponReader = issuedCouponReader;
        this.paymentReader = paymentReader;
    }

    @Test
    @DisplayName("결제 거절 시 차감 재고를 복원하고 장바구니는 유지하며 결제는 사유와 함께 FAILED로 남는다")
    void paymentDeclineRestoresStockAndKeepsCart() {
        UUID memberId = registerMember();
        // 3,333 × 3 = 9,999 — fake PG 거절 트리거(끝 세 자리 999)
        UUID variantId = seedProduct(Money.of(3333L), 50);
        cartAppender.addItem(memberId, variantId, 3);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.PAYMENT_DECLINED));

        verify(orderModifier).cancel(any(UUID.class), eq(CancellationReason.PAYMENT_FAILED));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(cartReader.getCart(memberId).items()).hasSize(1);
        UUID orderId = orderIdCancelled();
        assertThat(paymentReader.getByOrderId(orderId).failureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("쿠폰 체크아웃 결제 거절 시 쿠폰을 ISSUED로 복원한다")
    void paymentDeclineRestoresCoupon() {
        UUID memberId = registerMember();
        // 5,555 × 2 = 11,110 − 10% 할인 1,111 = 9,999 — fake PG 거절 트리거
        UUID variantId = seedProduct(Money.of(5555L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30, null);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD))
                .isInstanceOf(ApiException.class);

        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = seedOnSaleProduct("상품", null, price, quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private UUID orderIdCancelled() {
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(orderModifier).cancel(captor.capture(), any(CancellationReason.class));
        return captor.getValue();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }
}

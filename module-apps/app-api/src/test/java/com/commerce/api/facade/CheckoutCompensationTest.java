package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.coupon.entity.Discount;
import com.commerce.coupon.entity.IssuedCouponStatus;
import com.commerce.coupon.entity.ValidityPeriod;
import com.commerce.coupon.service.CouponAppender;
import com.commerce.coupon.service.IssuedCouponAppender;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.service.MemberAppender;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.service.OrderModifier;
import com.commerce.payment.entity.FailureReason;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.port.PaymentApproval;
import com.commerce.payment.port.PaymentGateway;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.service.StockReader;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 결제 실패 경로를 declined 반환 테스트 더블 게이트웨이로 태워 동기 보상을 검증한다. */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(CheckoutCompensationTest.DecliningGatewayConfig.class)
class CheckoutCompensationTest extends FacadeIntegrationTest {

    @MockitoSpyBean
    private OrderModifier orderModifier;

    private final CheckoutFacade checkoutFacade;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final MemberAppender memberAppender;
    private final CartAppender cartAppender;
    private final CouponAppender couponAppender;
    private final IssuedCouponAppender issuedCouponAppender;
    private final CartReader cartReader;
    private final StockReader stockReader;
    private final ProductVariantReader variantReader;
    private final IssuedCouponReader issuedCouponReader;

    CheckoutCompensationTest(
            CheckoutFacade checkoutFacade,
            ProductRegistrationFacade productRegistrationFacade,
            MemberAppender memberAppender,
            CartAppender cartAppender,
            CouponAppender couponAppender,
            IssuedCouponAppender issuedCouponAppender,
            CartReader cartReader,
            StockReader stockReader,
            ProductVariantReader variantReader,
            IssuedCouponReader issuedCouponReader) {
        this.checkoutFacade = checkoutFacade;
        this.productRegistrationFacade = productRegistrationFacade;
        this.memberAppender = memberAppender;
        this.cartAppender = cartAppender;
        this.couponAppender = couponAppender;
        this.issuedCouponAppender = issuedCouponAppender;
        this.cartReader = cartReader;
        this.stockReader = stockReader;
        this.variantReader = variantReader;
        this.issuedCouponReader = issuedCouponReader;
    }

    @Test
    @DisplayName("결제 거절 시 차감 재고를 복원하고 장바구니는 유지한다")
    void paymentDeclineRestoresStockAndKeepsCart() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 3);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.PAYMENT_DECLINED));

        verify(orderModifier).cancel(any(UUID.class), eq(CancellationReason.PAYMENT_FAILED));
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
        assertThat(cartReader.getCart(memberId).items()).hasSize(1);
    }

    @Test
    @DisplayName("쿠폰 체크아웃 결제 거절 시 쿠폰을 ISSUED로 복원한다")
    void paymentDeclineRestoresCoupon() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(Money.of(10000L), 50);
        cartAppender.addItem(memberId, variantId, 2);
        UUID couponId = couponAppender.create("10% 할인", Discount.rate(10), Money.of(10000L), validity(), 30);
        UUID issuedId = issuedCouponAppender.issue(couponId, memberId);

        assertThatThrownBy(() -> checkoutFacade.checkout(memberId, address(), Money.ZERO, issuedId, PaymentMethod.CARD))
                .isInstanceOf(ApiException.class);

        assertThat(issuedCouponReader.getIssuedCoupon(issuedId, memberId).status())
                .isEqualTo(IssuedCouponStatus.ISSUED);
        assertThat(stockReader.getByVariantId(variantId).quantity()).isEqualTo(50);
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터");
    }

    private UUID seedProduct(Money price, int quantity) {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, price, List.of(), quantity);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private static Address address() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }

    private static ValidityPeriod validity() {
        return ValidityPeriod.of(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
    }

    @TestConfiguration
    static class DecliningGatewayConfig {

        @Bean
        @Primary
        PaymentGateway decliningPaymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentApproval approve(Money amount, PaymentMethod method) {
                    return PaymentApproval.declined(FailureReason.INSUFFICIENT_BALANCE);
                }

                @Override
                public String cancel(String pgTransactionId, String idempotencyKey) {
                    return "TEST-CANCEL-" + pgTransactionId;
                }
            };
        }
    }
}

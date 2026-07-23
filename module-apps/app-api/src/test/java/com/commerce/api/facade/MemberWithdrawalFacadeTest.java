package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.application.provided.CartAppender;
import com.commerce.member.application.provided.MemberAppender;
import com.commerce.member.application.provided.MemberReader;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.domain.WithdrawalReason;
import com.commerce.member.domain.exception.MemberNotFoundException;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.domain.Address;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MemberWithdrawalFacadeTest extends FacadeIntegrationTest {

    private final MemberWithdrawalFacade memberWithdrawalFacade;
    private final CheckoutFacade checkoutFacade;
    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final CartAppender cartAppender;
    private final ProductVariantReader variantReader;
    private final OrderModifier orderModifier;

    MemberWithdrawalFacadeTest(
            MemberWithdrawalFacade memberWithdrawalFacade,
            CheckoutFacade checkoutFacade,
            MemberAppender memberAppender,
            MemberReader memberReader,
            CartAppender cartAppender,
            ProductVariantReader variantReader,
            OrderModifier orderModifier) {
        this.memberWithdrawalFacade = memberWithdrawalFacade;
        this.checkoutFacade = checkoutFacade;
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.cartAppender = cartAppender;
        this.variantReader = variantReader;
        this.orderModifier = orderModifier;
    }

    @Test
    @DisplayName("미배송 결제 주문이 있으면 탈퇴가 거부된다")
    void withdrawalBlockedByUndeliveredPaidOrder() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);

        assertThatThrownBy(() -> memberWithdrawalFacade.withdraw(memberId, WithdrawalReason.NO_LONGER_USED))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.WITHDRAWAL_BLOCKED));
        assertThat(memberReader.getMember(memberId).status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("막는 주문이 없으면 탈퇴가 논리삭제한다")
    void withdrawalAllowedWithoutBlockingOrder() {
        UUID memberId = registerMember();

        memberWithdrawalFacade.withdraw(memberId, WithdrawalReason.NO_LONGER_USED);

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("배송 완료 주문은 탈퇴를 막지 않는다")
    void withdrawalAllowedAfterDelivery() {
        UUID memberId = registerMember();
        UUID variantId = seedProduct(50);
        cartAppender.addItem(memberId, variantId, 1);
        UUID orderId = checkoutFacade.checkout(memberId, address(), Money.ZERO, null, PaymentMethod.CARD);
        orderModifier.ship(orderId, "CJ대한통운", "688900123456");
        orderModifier.confirmDelivery(orderId);

        memberWithdrawalFacade.withdraw(memberId, WithdrawalReason.NO_LONGER_USED);

        assertThatThrownBy(() -> memberReader.getMember(memberId)).isInstanceOf(MemberNotFoundException.class);
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
}

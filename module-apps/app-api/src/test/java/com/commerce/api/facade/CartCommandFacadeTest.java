package com.commerce.api.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.exception.MemberNotFoundException;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberRemover;
import com.commerce.product.service.ProductModifier;
import com.commerce.product.service.ProductRemover;
import com.commerce.product.service.ProductVariantModifier;
import com.commerce.product.service.ProductVariantReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartCommandFacadeTest extends FacadeIntegrationTest {

    private final CartCommandFacade cartCommandFacade;
    private final CartReader cartReader;
    private final MemberAppender memberAppender;
    private final MemberModifier memberModifier;
    private final MemberRemover memberRemover;
    private final ProductRegistrationFacade productRegistrationFacade;
    private final ProductVariantReader variantReader;
    private final ProductVariantModifier variantModifier;
    private final ProductModifier productModifier;
    private final ProductRemover productRemover;

    CartCommandFacadeTest(
            CartCommandFacade cartCommandFacade,
            CartReader cartReader,
            MemberAppender memberAppender,
            MemberModifier memberModifier,
            MemberRemover memberRemover,
            ProductRegistrationFacade productRegistrationFacade,
            ProductVariantReader variantReader,
            ProductVariantModifier variantModifier,
            ProductModifier productModifier,
            ProductRemover productRemover) {
        this.cartCommandFacade = cartCommandFacade;
        this.cartReader = cartReader;
        this.memberAppender = memberAppender;
        this.memberModifier = memberModifier;
        this.memberRemover = memberRemover;
        this.productRegistrationFacade = productRegistrationFacade;
        this.variantReader = variantReader;
        this.variantModifier = variantModifier;
        this.productModifier = productModifier;
        this.productRemover = productRemover;
    }

    @Test
    @DisplayName("자격 활성 회원이 주문 가능 변형을 담으면 라인이 생긴다")
    void addsItemForEligibleMemberAndOrderableVariant() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();

        cartCommandFacade.addItem(memberId, variantId, 2);

        assertThat(quantityOf(memberId, variantId)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 변형 재담기는 수량을 합산한다")
    void sumsQuantityOnReAdd() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();

        cartCommandFacade.addItem(memberId, variantId, 2);
        cartCommandFacade.addItem(memberId, variantId, 3);

        assertThat(quantityOf(memberId, variantId)).isEqualTo(5);
    }

    @Test
    @DisplayName("정지 회원의 담기는 자격 미달로 거부한다")
    void addRejectedForSuspendedMember() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        memberModifier.suspend(memberId, SuspensionReason.POLICY_VIOLATION);

        assertThatThrownBy(() -> cartCommandFacade.addItem(memberId, variantId, 1))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.MEMBER_NOT_ELIGIBLE));
        assertThat(cartReader.getCart(memberId).items()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 회원의 담기는 회원 미존재로 거부한다")
    void addRejectedForWithdrawnMember() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        memberRemover.delete(memberId, WithdrawalReason.NO_LONGER_USED);

        assertThatThrownBy(() -> cartCommandFacade.addItem(memberId, variantId, 1))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("비활성 변형의 담기는 주문 불가로 거부한다")
    void addRejectedForDisabledVariant() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        variantModifier.disable(variantId);

        assertThatThrownBy(() -> cartCommandFacade.addItem(memberId, variantId, 1))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_ORDERABLE));
        assertThat(cartReader.getCart(memberId).items()).isEmpty();
    }

    @Test
    @DisplayName("숨김 상품의 변형 담기는 주문 불가로 거부한다")
    void addRejectedForHiddenProduct() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        productModifier.hide(variantReader.getVariant(variantId).productId());

        assertThatThrownBy(() -> cartCommandFacade.addItem(memberId, variantId, 1))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_ORDERABLE));
    }

    @Test
    @DisplayName("삭제 상품의 변형 담기는 주문 불가로 거부한다")
    void addRejectedForDeletedProduct() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        productRemover.delete(variantReader.getVariant(variantId).productId());

        assertThatThrownBy(() -> cartCommandFacade.addItem(memberId, variantId, 1))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_ORDERABLE));
    }

    @Test
    @DisplayName("증량은 담기 게이트를 받아 비활성 변형이면 거부하고 수량을 유지한다")
    void increaseGatedByVariantState() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartCommandFacade.addItem(memberId, variantId, 1);
        variantModifier.disable(variantId);

        assertThatThrownBy(() -> cartCommandFacade.changeItemQuantity(memberId, variantId, 3))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_ORDERABLE));
        assertThat(quantityOf(memberId, variantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("감량은 게이트 없이 허용해 비활성 변형 라인도 줄일 수 있다")
    void decreaseAllowedRegardlessOfVariantState() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartCommandFacade.addItem(memberId, variantId, 3);
        variantModifier.disable(variantId);

        cartCommandFacade.changeItemQuantity(memberId, variantId, 1);

        assertThat(quantityOf(memberId, variantId)).isEqualTo(1);
    }

    @Test
    @DisplayName("제거는 게이트 없이 허용해 비활성 변형 라인도 정리할 수 있다")
    void removeItemAllowedRegardlessOfVariantState() {
        UUID memberId = registerMember();
        UUID variantId = seedVariant();
        cartCommandFacade.addItem(memberId, variantId, 1);
        variantModifier.disable(variantId);

        cartCommandFacade.removeItem(memberId, variantId);

        assertThat(cartReader.getCart(memberId).items()).isEmpty();
    }

    private UUID registerMember() {
        return memberAppender.register("user-" + UUID.randomUUID() + "@example.com", "테스터", "password-123!");
    }

    private UUID seedVariant() {
        UUID productId = productRegistrationFacade.registerProduct("상품", null, Money.of(10000L), List.of(), 50);
        return variantReader.getByProductId(productId).get(0).id();
    }

    private int quantityOf(UUID memberId, UUID variantId) {
        return cartReader.getCart(memberId).items().stream()
                .filter(item -> item.variantId().equals(variantId))
                .map(CartItemInfo::quantity)
                .findFirst()
                .orElse(0);
    }
}

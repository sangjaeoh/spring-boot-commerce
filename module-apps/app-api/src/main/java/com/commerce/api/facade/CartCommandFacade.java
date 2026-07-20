package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.service.CartAppender;
import com.commerce.cart.service.CartModifier;
import com.commerce.cart.service.CartReader;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberReader;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.exception.ProductVariantNotFoundException;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 장바구니 쓰기를 조율하며 담기·증량에 주문 자격 게이트를 적용하는 파사드다. */
@Component
public class CartCommandFacade {

    private final MemberReader memberReader;
    private final ProductVariantReader variantReader;
    private final ProductReader productReader;
    private final CartReader cartReader;
    private final CartAppender cartAppender;
    private final CartModifier cartModifier;

    public CartCommandFacade(
            MemberReader memberReader,
            ProductVariantReader variantReader,
            ProductReader productReader,
            CartReader cartReader,
            CartAppender cartAppender,
            CartModifier cartModifier) {
        this.memberReader = memberReader;
        this.variantReader = variantReader;
        this.productReader = productReader;
        this.cartReader = cartReader;
        this.cartAppender = cartAppender;
        this.cartModifier = cartModifier;
    }

    /**
     * 변형을 장바구니에 담는다. 같은 변형은 수량을 합산한다.
     *
     * @throws ApiException 회원 자격 비활성; 주문 불가(변형 비활성·상품 HIDDEN·삭제)
     */
    public void addItem(UUID memberId, UUID variantId, int quantity) {
        // 1. 주문 자격 게이트
        requireAddable(memberId, variantId);
        // 2. 장바구니 담기
        cartAppender.addItem(memberId, variantId, quantity);
    }

    /**
     * 라인 수량을 바꾼다. 증량은 담기와 같은 자격 게이트를, 감량·유지는 게이트 없이 적용한다.
     *
     * @throws ApiException 증량 시 회원 자격 비활성; 주문 불가(변형 비활성·상품 HIDDEN·삭제)
     */
    public void changeItemQuantity(UUID memberId, UUID variantId, int quantity) {
        // 1. 증량이면 담기와 같은 주문 자격 게이트
        if (isIncrease(memberId, variantId, quantity)) {
            requireAddable(memberId, variantId);
        }
        // 2. 라인 수량 변경
        cartModifier.changeItemQuantity(memberId, variantId, quantity);
    }

    /** 라인을 제거한다. 자격 게이트 없이 허용한다. */
    public void removeItem(UUID memberId, UUID variantId) {
        cartModifier.removeItem(memberId, variantId);
    }

    /** 장바구니를 비운다. 자격 게이트 없이 허용한다. */
    public void clear(UUID memberId) {
        cartModifier.clear(memberId);
    }

    /** 회원 자격·변형·상품 게이트를 모두 통과시킨다. */
    private void requireAddable(UUID memberId, UUID variantId) {
        // 1. 회원 자격 확인
        requireEligibleMember(memberId);
        // 2. 변형 활성 확인
        ProductVariantInfo variant = requireActiveVariant(variantId);
        // 3. 변형이 속한 상품 판매중 확인
        requireOnSaleProduct(variant.productId());
    }

    /** 새 수량이 기존 라인 수량보다 큰지 판정한다. */
    private boolean isIncrease(UUID memberId, UUID variantId, int newQuantity) {
        // 라인이 없으면 증량이 아니다 — 게이트를 건너뛰어 도메인의 changeItemQuantity가 라인 미존재를 보고한다.
        return cartReader.getCart(memberId).items().stream()
                .filter(item -> item.variantId().equals(variantId))
                .findFirst()
                .map(item -> newQuantity > item.quantity())
                .orElse(false);
    }

    /** 회원이 활성 자격인지 확인한다. */
    private void requireEligibleMember(UUID memberId) {
        MemberInfo member = memberReader.getMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_NOT_ELIGIBLE);
        }
    }

    /** 변형이 활성인지 확인한다. 미존재도 주문 불가로 합친다. */
    private ProductVariantInfo requireActiveVariant(UUID variantId) {
        ProductVariantInfo variant;
        try {
            variant = variantReader.getVariant(variantId);
        } catch (ProductVariantNotFoundException e) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (variant.status() != ProductVariantStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        return variant;
    }

    /** 상품이 판매중인지 확인한다. 미존재도 주문 불가로 합친다. */
    private void requireOnSaleProduct(UUID productId) {
        ProductInfo product;
        try {
            product = productReader.getProduct(productId);
        } catch (ProductNotFoundException e) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (product.status() != ProductStatus.ON_SALE) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
    }
}

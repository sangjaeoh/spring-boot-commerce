package com.commerce.app.api.facade.view;

import com.commerce.domain.shared.entity.Money;

/**
 * 체크아웃 미리보기 합성 뷰다.
 *
 * @param totalAmount 라인 합계(할인 전 상품 금액)
 * @param discountAmount 쿠폰 할인액(쿠폰 미적용이면 0)
 * @param shippingFee 배송비
 * @param payAmount 결제 예정액(합계 - 할인 + 배송비)
 */
public record CheckoutPreviewView(Money totalAmount, Money discountAmount, Money shippingFee, Money payAmount) {}

package com.commerce.cart.application.provided;

import com.commerce.cart.application.info.CartInfo;
import java.util.UUID;

/** 장바구니 조회를 담당하는 서비스다. */
public interface CartReader {

    /** 회원의 장바구니를 조회한다. 없으면 빈 장바구니를 반환한다. */
    CartInfo getCart(UUID memberId);
}

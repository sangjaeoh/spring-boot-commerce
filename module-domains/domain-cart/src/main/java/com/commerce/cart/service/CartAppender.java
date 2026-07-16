package com.commerce.cart.service;

import com.commerce.cart.entity.Cart;
import com.commerce.cart.repository.CartRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장바구니 담기를 담당한다.
 *
 * <p>동시 담기의 삽입 경합은 한 번의 재조회-재시도로 흡수한다 — 최초 담기 2건의 장바구니 중복 생성
 * (member_id 유니크)과 동일 변형 신규 라인 중복 생성(cart_id·variant_id 유니크)은 유니크 위반을 잡아
 * 상대가 커밋한 장바구니·라인 위에서 합산으로 다시 시도한다(product·stock Appender의 유니크 경합 방어와
 * 같은 계열). 동일 라인 동시 합산은 {@code CartItem}의 낙관락이 유실을 막고, 충돌은 재시도 없이
 * 전파한다(409, 클라이언트 재시도 — {@code docs/entity-persistence.md}의 무재시도 규약).
 */
@Service
public class CartAppender {

    private final CartRepository cartRepository;
    private final TransactionTemplate transactionTemplate;

    public CartAppender(CartRepository cartRepository, PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 회원 장바구니가 없으면 만들고 변형을 담는다. 같은 변형은 수량을 합산한다. */
    public void addItem(UUID memberId, UUID variantId, int quantity) {
        try {
            addItemOnce(memberId, variantId, quantity);
        } catch (DataIntegrityViolationException e) {
            // 동시 최초 담기·동일 변형 동시 신규 담기 경합 — 상대 커밋을 재조회해 합산으로 수렴한다.
            addItemOnce(memberId, variantId, quantity);
        }
    }

    private void addItemOnce(UUID memberId, UUID variantId, int quantity) {
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart =
                    cartRepository.findByMemberId(memberId).orElseGet(() -> cartRepository.save(Cart.create(memberId)));
            cart.addItem(variantId, quantity);
            // 유니크 위반을 이 시도의 트랜잭션 안에서 표면화해, 커밋 시점이 아니라 여기서 잡아 재시도한다.
            cartRepository.flush();
        });
    }
}

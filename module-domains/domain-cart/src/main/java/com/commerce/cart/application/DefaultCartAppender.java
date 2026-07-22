package com.commerce.cart.application;

import com.commerce.cart.application.provided.CartAppender;
import com.commerce.cart.application.required.CartRepository;
import com.commerce.cart.domain.Cart;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link CartAppender}의 기본 구현이다. */
@Service
class DefaultCartAppender implements CartAppender {

    private final CartRepository cartRepository;
    private final TransactionTemplate transactionTemplate;

    DefaultCartAppender(CartRepository cartRepository, PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // 재시도가 상대의 커밋을 보려면 시도마다 독립 물리 트랜잭션이어야 한다. 외부 트랜잭션에 합류하면
        // 1차 시도의 유니크 위반이 그 트랜잭션을 rollback-only로 오염시켜 재시도가 성립하지 않는다.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void addItem(UUID memberId, UUID variantId, int quantity) {
        try {
            addItemOnce(memberId, variantId, quantity);
        } catch (DataIntegrityViolationException e) {
            // 동시 최초 담기·동일 변형 동시 신규 담기 경합 — 상대 커밋을 재조회해 합산으로 수렴한다.
            addItemOnce(memberId, variantId, quantity);
        }
    }

    /** 장바구니가 없으면 만들고 담기를 한 번 시도한다. */
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

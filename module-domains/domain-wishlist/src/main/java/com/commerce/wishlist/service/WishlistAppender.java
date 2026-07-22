package com.commerce.wishlist.service;

import com.commerce.wishlist.entity.WishlistItem;
import com.commerce.wishlist.repository.WishlistItemRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 찜 추가를 담당하는 서비스다. */
@Service
public class WishlistAppender {

    private final WishlistItemRepository wishlistItemRepository;
    private final TransactionTemplate transactionTemplate;

    public WishlistAppender(
            WishlistItemRepository wishlistItemRepository, PlatformTransactionManager transactionManager) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // 유니크 위반을 no-op으로 삼키려면 시도가 독립 물리 트랜잭션이어야 한다. 외부 트랜잭션에 합류하면
        // 위반이 그 트랜잭션을 rollback-only로 오염시킨다.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 상품을 찜한다. 이미 찜한 상품이면 아무 일도 하지 않는다(멱등). */
    public void add(UUID memberId, UUID productId) {
        try {
            addOnce(memberId, productId);
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 찜 경합 — 상대가 이미 찜해 원하는 종결 상태(찜 존재)가 달성됐다.
        }
    }

    /** 찜이 없으면 저장을 한 번 시도한다. */
    private void addOnce(UUID memberId, UUID productId) {
        transactionTemplate.executeWithoutResult(status -> {
            if (wishlistItemRepository.existsByMemberIdAndProductId(memberId, productId)) {
                return;
            }
            wishlistItemRepository.save(WishlistItem.create(memberId, productId));
            // 유니크 위반을 이 시도의 트랜잭션 안에서 표면화해, 커밋 시점이 아니라 여기서 잡는다.
            wishlistItemRepository.flush();
        });
    }
}

package com.commerce.domain.wishlist.application;

import com.commerce.domain.member.application.info.MemberInfo;
import com.commerce.domain.member.application.provided.MemberReader;
import com.commerce.domain.product.application.provided.ProductReader;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.wishlist.application.required.MailGateway;
import com.commerce.domain.wishlist.application.required.RestockNotificationRepository;
import com.commerce.domain.wishlist.application.required.WishlistItemRepository;
import com.commerce.domain.wishlist.domain.RestockNotification;
import com.commerce.domain.wishlist.domain.WishlistItem;
import com.commerce.event.stock.StockRestocked;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link StockRestocked}를 소비해 재입고 상품을 찜한 활성 회원에게 알림 메일을 보내는 소비자다.
 *
 * <p>처리 기록(이벤트 식별자 유니크)이 중복 전달을 멱등하게 만든다. 발송은 취소 불가 부작용이라 트랜잭션
 * 밖에서 수행하고 처리 기록만 영속한다 — 발송 후 기록이므로 크래시 창은 재발송(at-least-once)이다.
 */
@Component
class StockRestockedListener {

    private final WishlistItemRepository wishlistItemRepository;
    private final RestockNotificationRepository restockNotificationRepository;
    private final ProductVariantReader productVariantReader;
    private final ProductReader productReader;
    private final MemberReader memberReader;
    private final MailGateway mailGateway;

    StockRestockedListener(
            WishlistItemRepository wishlistItemRepository,
            RestockNotificationRepository restockNotificationRepository,
            ProductVariantReader productVariantReader,
            ProductReader productReader,
            MemberReader memberReader,
            MailGateway mailGateway) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.restockNotificationRepository = restockNotificationRepository;
        this.productVariantReader = productVariantReader;
        this.productReader = productReader;
        this.memberReader = memberReader;
        this.mailGateway = mailGateway;
    }

    /** 재입고 이벤트를 받아 찜한 활성 회원에게 알림 메일을 보내고 처리 기록을 남긴다. */
    @EventListener
    public void on(StockRestocked event) {
        // 1. 멱등 가드 — 이미 처리한 이벤트는 스킵
        if (restockNotificationRepository.existsByEventId(event.eventId())) {
            return;
        }

        // 2. 수신자 해석 — 변형→상품, 상품 찜 회원 중 활성 회원의 이메일
        UUID productId = productVariantReader.getVariant(event.variantId()).productId();
        String productName = productReader.getProduct(productId).name();
        List<UUID> wisherIds = wishlistItemRepository.findAllByProductId(productId).stream()
                .map(WishlistItem::getMemberId)
                .toList();

        // 3. 발송(트랜잭션 밖 부작용) 후 처리 기록
        if (!wisherIds.isEmpty()) {
            for (MemberInfo member : memberReader.getMembers(wisherIds)) {
                mailGateway.sendRestockMail(member.email(), productName);
            }
        }
        restockNotificationRepository.save(RestockNotification.create(event.eventId()));
    }
}

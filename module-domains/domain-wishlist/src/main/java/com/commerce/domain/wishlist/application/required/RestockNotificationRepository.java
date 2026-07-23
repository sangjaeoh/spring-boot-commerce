package com.commerce.domain.wishlist.application.required;

import com.commerce.domain.wishlist.domain.RestockNotification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockNotificationRepository extends JpaRepository<RestockNotification, UUID> {

    boolean existsByEventIdAndMemberId(UUID eventId, UUID memberId);
}

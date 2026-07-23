package com.commerce.domain.wishlist.domain;

import com.commerce.common.core.id.UuidV7Generator;
import com.commerce.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** 재입고 이벤트의 회원별 알림 처리 기록이다. (eventId, memberId) 조합 유니크로 회원 단위 중복 발송을 멱등하게 만든다. */
@Entity
@Table(schema = "wishlist", name = "restock_notification")
public class RestockNotification extends BaseTimeEntity<UUID> {

    /** 처리 기록 식별자. 생성 시각 순서를 담은 UUIDv7. */
    @Id
    private UUID id;

    /** 처리한 재입고 이벤트 식별자. (eventId, memberId) 조합 유니크. */
    @Column(name = "event_id")
    private UUID eventId;

    /** 발송 대상 회원 식별자. (eventId, memberId) 조합 유니크. */
    @Column(name = "member_id")
    private UUID memberId;

    protected RestockNotification() {}

    private RestockNotification(UUID id, UUID eventId, UUID memberId) {
        this.id = id;
        this.eventId = eventId;
        this.memberId = memberId;
    }

    /** 이벤트·회원 단위 처리 기록을 만든다. */
    public static RestockNotification create(UUID eventId, UUID memberId) {
        return new RestockNotification(UuidV7Generator.generate(), eventId, memberId);
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getMemberId() {
        return memberId;
    }
}

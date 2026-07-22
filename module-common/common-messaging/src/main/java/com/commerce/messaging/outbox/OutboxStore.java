package com.commerce.messaging.outbox;

import java.util.List;
import java.util.UUID;

/** 아웃박스 릴레이가 소비하는 미발행 이벤트 저장소의 벤더 중립 포트다. */
public interface OutboxStore {

    /** 미발행 이벤트를 생성순으로 최대 {@code limit}건 조회한다. */
    List<OutboxMessage> fetchUnpublished(int limit);

    /** 이벤트를 발행 완료로 표시한다. 반복 호출에 멱등하다. */
    void markPublished(UUID id);
}

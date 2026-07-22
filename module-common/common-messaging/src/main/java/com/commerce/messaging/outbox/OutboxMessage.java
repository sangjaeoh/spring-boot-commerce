package com.commerce.messaging.outbox;

import java.util.UUID;

/**
 * 아웃박스에 영속된 미발행 이벤트의 경계 record다.
 *
 * @param eventType 이벤트 클래스 FQCN. 릴레이가 역직렬화 대상 타입을 복원하는 키다
 * @param payload 이벤트 JSON 직렬화 본문
 */
public record OutboxMessage(UUID id, String eventType, String payload) {}

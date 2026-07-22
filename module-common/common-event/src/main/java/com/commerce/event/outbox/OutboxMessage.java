package com.commerce.event.outbox;

import java.util.UUID;

/**
 * 아웃박스에 영속된 미발행 이벤트의 경계 record다.
 *
 * @param eventType 논리 타입 키({@code {도메인}.{이벤트명}}). 릴레이가 역직렬화 대상 타입을 해석하는 키다
 * @param payload 이벤트 JSON 직렬화 본문
 */
public record OutboxMessage(UUID id, String eventType, String payload) {}

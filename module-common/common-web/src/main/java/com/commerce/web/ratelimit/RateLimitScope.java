package com.commerce.web.ratelimit;

/**
 * 레이트리밋 필터가 보호하는 한 표면의 카운터 키와 거부 응답 문구를 정하는 값이다.
 *
 * <p>{@code keyPrefix}는 클라이언트 키 앞에 붙어 표면별 카운터를 분리한다 — 같은 저장소를 공유하는 여러 표면
 * (로그인·가입)의 시도가 서로의 한도를 소모하지 않게 하는 키 네임스페이스다. 나머지는 한도 초과({@code 429})와
 * 저장소 불가({@code 503}) problem+json 본문의 {@code code}·{@code detail} 문구를 표면에 맞춘다.
 */
public record RateLimitScope(String keyPrefix, String tooManyCode, String tooManyDetail, String unavailableDetail) {}

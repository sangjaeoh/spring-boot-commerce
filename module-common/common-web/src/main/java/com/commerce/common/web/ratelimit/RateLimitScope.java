package com.commerce.common.web.ratelimit;

/**
 * 레이트리밋 필터가 보호하는 한 표면의 카운터 키와 거부 응답 문구를 정하는 값이다.
 *
 * @param keyPrefix 클라이언트 키 앞에 붙는 접두사. 같은 저장소를 쓰는 표면들의 카운터를 분리한다
 * @param tooManyCode 한도 초과({@code 429}) 응답 본문의 {@code code}
 * @param tooManyDetail 한도 초과({@code 429}) 응답 본문의 {@code detail}
 * @param unavailableDetail 저장소 불가({@code 503}) 응답 본문의 {@code detail}
 */
public record RateLimitScope(String keyPrefix, String tooManyCode, String tooManyDetail, String unavailableDetail) {}

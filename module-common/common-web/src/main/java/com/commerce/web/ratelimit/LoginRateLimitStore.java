package com.commerce.web.ratelimit;

import java.time.Duration;

/** 클라이언트 키의 고정창 시도 수를 세는 벤더 중립 저장소 포트다. */
public interface LoginRateLimitStore {

    /**
     * 키의 현재 고정창 시도 수를 1 증가시키고 증가 후 값을 돌려준다. 그 키에 진행 중인 창이 없으면
     * {@code window}만큼의 새 창을 시작한다(창 경과 후 첫 증가는 1을 돌려준다).
     *
     * @throws RuntimeException 저장소에 접근할 수 없으면 전파한다(fail-closed)
     */
    long incrementAndCount(String key, Duration window);
}

package com.commerce.auth.token;

import java.time.Duration;
import java.util.Optional;

/** 리프레시 토큰을 TTL 동안 보관·조회·삭제하는 저장소 포트다. */
public interface RefreshTokenStore {

    /** 토큰을 주체와 함께 TTL 동안 보관한다. */
    void save(String token, String subject, Duration ttl);

    /** 보관 중인 토큰이면 주체를 반환하고, 아니면(미발급·만료·삭제) 빈 결과를 반환한다. */
    Optional<String> findSubject(String token);

    /** 토큰을 삭제한다. 없는 토큰이면 아무 일도 하지 않는다. */
    void delete(String token);
}

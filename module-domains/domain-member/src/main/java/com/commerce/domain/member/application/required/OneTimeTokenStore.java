package com.commerce.domain.member.application.required;

import java.time.Duration;
import java.util.Optional;

/** 1회용 토큰을 네임스페이스별로 TTL 동안 보관하고 소비 시 지우는 저장소 포트다. */
public interface OneTimeTokenStore {

    /** 토큰을 네임스페이스 안에서 주체와 함께 TTL 동안 보관한다. */
    void save(String namespace, String token, String subject, Duration ttl);

    /** 보관 중인 토큰이면 주체를 반환하며 지우고, 아니면(미발급·만료·기소비) 빈 결과를 반환한다. */
    Optional<String> consume(String namespace, String token);
}

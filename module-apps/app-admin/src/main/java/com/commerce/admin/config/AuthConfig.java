package com.commerce.admin.config;

import com.commerce.auth.token.JwtTokenCodec;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JWT 토큰 코덱을 배선하는 설정이다. 발급은 app-api가 소유하고 이 앱은 같은 키로 검증만 한다. */
@Configuration
public class AuthConfig {

    /** 설정으로 주입된 서명 키·액세스 토큰 TTL로 조립한 코덱을 공급한다. */
    @Bean
    JwtTokenCodec jwtTokenCodec(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-token-ttl}") Duration ttl,
            Clock clock) {
        return new JwtTokenCodec(secret, ttl, clock);
    }
}

package com.commerce.api.config;

import com.commerce.auth.token.JwtTokenCodec;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JWT 토큰 코덱을 서명 키(환경변수 주입)·TTL 설정으로 조립한다. */
@Configuration
public class AuthConfig {

    @Bean
    JwtTokenCodec jwtTokenCodec(
            @Value("${auth.jwt.secret}") String secret, @Value("${auth.jwt.access-token-ttl}") Duration ttl) {
        return new JwtTokenCodec(secret, ttl);
    }
}

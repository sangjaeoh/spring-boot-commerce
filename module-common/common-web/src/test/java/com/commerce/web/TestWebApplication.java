package com.commerce.web;

import com.commerce.auth.token.JwtTokenCodec;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * common-web 웹 계층 하네스를 부팅하는 테스트 전용 앱이다. {@code com.commerce.web}를 스캔해 핸들러·
 * 필터·저장소와 테스트 컨트롤러를 실제 컨텍스트에 등록한다.
 */
@SpringBootApplication
public class TestWebApplication {

    /** 인증 필터가 요구하는 토큰 코덱. 실행 앱에서는 앱 설정이 키·TTL을 주입해 등록한다. */
    @Bean
    JwtTokenCodec jwtTokenCodec() {
        return new JwtTokenCodec("test-secret-key-of-at-least-32-bytes!!", Duration.ofHours(1), Clock.systemUTC());
    }
}

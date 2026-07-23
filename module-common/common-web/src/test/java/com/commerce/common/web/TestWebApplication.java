package com.commerce.common.web;

import com.commerce.common.auth.token.JwtTokenCodec;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * common-web 웹 계층 하네스를 부팅하는 테스트 전용 앱이다. {@code com.commerce.common.web}를 스캔해 핸들러·
 * 필터·저장소와 테스트 컨트롤러를 실제 컨텍스트에 등록한다.
 */
// security가 테스트 클래스패스에 오르면 Boot 기본 보안체인(ServletWebSecurityAutoConfiguration)이
// /test/* 를 전부 인증 뒤로 잠근다. 이 앱은 커스텀 SecurityFilterChain이 없어, 필터·핸들러 테스트를
// 격리하려 시큐리티 오토컨피그를 배제한다(코어 전환 슬라이스에서 앱이 커스텀 체인을 소유한다).
@SpringBootApplication(
        exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class,
        })
public class TestWebApplication {

    /** 인증 필터가 요구하는 토큰 코덱을 등록한다. 실행 앱에서는 앱 설정이 키·TTL을 주입해 등록한다. */
    @Bean
    JwtTokenCodec jwtTokenCodec() {
        return new JwtTokenCodec("test-secret-key-of-at-least-32-bytes!!", Duration.ofHours(1), Clock.systemUTC());
    }
}

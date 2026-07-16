package com.commerce.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenCodecTest {

    private static final String SECRET = "test-secret-key-of-at-least-32-bytes!!";

    private final JwtTokenCodec codec = new JwtTokenCodec(SECRET, Duration.ofHours(1), Clock.systemUTC());

    @Test
    @DisplayName("발급한 토큰을 검증하면 주체·역할이 왕복한다")
    void issueThenVerifyRoundTripsSubjectAndRole() {
        UUID subject = UUID.randomUUID();

        String token = codec.issue(subject, AuthRole.ADMIN);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(codec.verify(token)).contains(new TokenClaims(subject, AuthRole.ADMIN));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 검증에 실패한다")
    void verifyRejectsTokenSignedWithDifferentKey() {
        JwtTokenCodec other =
                new JwtTokenCodec("another-secret-key-of-32-bytes-min!!!!", Duration.ofHours(1), Clock.systemUTC());

        String token = other.issue(UUID.randomUUID(), AuthRole.BUYER);

        assertThat(codec.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("주입 시계의 고정 시각으로 만료가 결정론적으로 판정된다")
    void verifyUsesInjectedClockForExpiry() {
        Instant issuedAt = Instant.parse("2025-06-15T00:00:00Z");
        Duration ttl = Duration.ofHours(1);
        JwtTokenCodec issuing = new JwtTokenCodec(SECRET, ttl, Clock.fixed(issuedAt, ZoneOffset.UTC));
        JwtTokenCodec afterExpiry =
                new JwtTokenCodec(SECRET, ttl, Clock.fixed(issuedAt.plus(ttl).plusSeconds(1), ZoneOffset.UTC));

        String token = issuing.issue(UUID.randomUUID(), AuthRole.BUYER);

        assertThat(issuing.verify(token)).isPresent();
        assertThat(afterExpiry.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void verifyRejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minus(Duration.ofMinutes(5));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", AuthRole.BUYER.name())
                .issueTime(Date.from(past.minus(Duration.ofMinutes(1))))
                .expirationTime(Date.from(past))
                .build();
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        expired.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(codec.verify(expired.serialize())).isEmpty();
    }

    @Test
    @DisplayName("역할 클레임이 없는 토큰은 검증에 실패한다")
    void verifyRejectsTokenWithoutRoleClaim() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .build();
        SignedJWT roleless = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        roleless.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(codec.verify(roleless.serialize())).isEmpty();
    }

    @Test
    @DisplayName("정의되지 않은 역할 값을 실은 토큰은 검증에 실패한다")
    void verifyRejectsUnknownRoleValue() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "SUPERUSER")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .build();
        SignedJWT unknownRole = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        unknownRole.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(codec.verify(unknownRole.serialize())).isEmpty();
    }

    @Test
    @DisplayName("변조된 토큰은 검증에 실패한다")
    void verifyRejectsTamperedToken() {
        String token = codec.issue(UUID.randomUUID(), AuthRole.BUYER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(codec.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("토큰 형식이 아니면 검증에 실패한다")
    void verifyRejectsMalformedToken() {
        assertThat(codec.verify("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("32바이트 미만 키는 생성 시점에 거부된다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtTokenCodec("too-short", Duration.ofHours(1), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0 이하 TTL은 생성 시점에 거부된다")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtTokenCodec(SECRET, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

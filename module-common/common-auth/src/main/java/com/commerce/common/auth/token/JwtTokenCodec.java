package com.commerce.common.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** JWT 액세스 토큰을 HS256 단일 대칭 키로 발급·검증하는 코덱이다. */
public final class JwtTokenCodec {

    // MACSigner(HS256)의 최소 키 길이. 미달 키는 배선 시점에 실패시켜 약한 키 운용을 막는다.
    private static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtTokenCodec(String secret, Duration accessTokenTtl, Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 한다");
        }
        if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("액세스 토큰 TTL은 0보다 커야 한다");
        }
        this.secret = secretBytes;
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
    }

    /** 주체를 {@code sub}로, 넘겨받은 커스텀 클레임을 그대로 싣고 TTL 만큼 유효한 서명 토큰을 발급한다. */
    public String issue(String subject, Map<String, String> claims) {
        Instant now = clock.instant();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(accessTokenTtl)));
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명에 실패했다", e);
        }
        return jwt.serialize();
    }

    /** 서명·만료·주체가 유효하면 주체와 커스텀 클레임을 반환하고, 아니면 빈 결과를 반환한다. */
    public Optional<TokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            String subject = claims.getSubject();
            if (expiration == null || expiration.toInstant().isBefore(clock.instant()) || subject == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(subject, customClaims(claims)));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    /** 등록 클레임({@code sub}·{@code exp}·{@code iat} 등)을 뺀 커스텀 클레임만 문자열로 추린다. */
    private static Map<String, String> customClaims(JWTClaimsSet claims) {
        Map<String, String> custom = new HashMap<>();
        claims.getClaims().forEach((name, value) -> {
            if (!JWTClaimsSet.getRegisteredNames().contains(name) && value != null) {
                custom.put(name, String.valueOf(value));
            }
        });
        return custom;
    }
}

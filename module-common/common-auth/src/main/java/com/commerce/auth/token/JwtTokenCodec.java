package com.commerce.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 액세스 토큰을 HS256 단일 대칭 키로 발급·검증한다.
 *
 * <p>토큰은 주체(회원 ID)를 {@code sub} 클레임으로, 역할을 {@code role} 클레임으로 싣는다. 검증은
 * 서명·만료·주체·역할 형식을 모두 통과해야 클레임을 반환하며, 어떤 실패든 원인을 구분하지 않고 빈
 * 결과로 응답한다.
 */
public final class JwtTokenCodec {

    private static final String ROLE_CLAIM = "role";

    // MACSigner(HS256)의 최소 키 길이. 미달 키는 배선 시점에 실패시켜 약한 키 운용을 막는다.
    private static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration accessTokenTtl;

    public JwtTokenCodec(String secret, Duration accessTokenTtl) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 한다");
        }
        if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("액세스 토큰 TTL은 0보다 커야 한다");
        }
        this.secret = secretBytes;
        this.accessTokenTtl = accessTokenTtl;
    }

    /** 주체를 {@code sub}로, 역할을 {@code role}로 싣고 TTL 만큼 유효한 서명 토큰을 발급한다. */
    public String issue(UUID subject, AuthRole role) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .claim(ROLE_CLAIM, role.name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(accessTokenTtl)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명에 실패했다", e);
        }
        return jwt.serialize();
    }

    /** 서명·만료·주체·역할 형식이 모두 유효하면 클레임을 반환하고, 아니면 빈 결과를 반환한다. */
    public Optional<TokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            String subject = claims.getSubject();
            String role = claims.getStringClaim(ROLE_CLAIM);
            if (expiration == null
                    || expiration.toInstant().isBefore(Instant.now())
                    || subject == null
                    || role == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(UUID.fromString(subject), AuthRole.valueOf(role)));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

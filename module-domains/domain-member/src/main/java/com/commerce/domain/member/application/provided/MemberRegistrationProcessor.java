package com.commerce.domain.member.application.provided;

import java.util.Optional;
import java.util.UUID;

/** 회원 가입 접수(인증 메일 발송)와 이메일 인증을 조율하는 서비스다. */
public interface MemberRegistrationProcessor {

    /**
     * 회원을 가입시키고 인증 토큰을 보관한 뒤 인증 메일을 보낸다.
     *
     * @return 가입된 회원 ID
     */
    UUID register(String email, String name, String rawPassword);

    /**
     * 인증 토큰을 소비해 이메일 소유 인증을 기록한다. 토큰은 1회용이다.
     *
     * @return 인증된 회원 ID. 무효 토큰(위조·만료·기사용)이면 빈 결과
     */
    Optional<UUID> verifyEmail(String token);
}

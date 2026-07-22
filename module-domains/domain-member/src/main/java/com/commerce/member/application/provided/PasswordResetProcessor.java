package com.commerce.member.application.provided;

import java.util.Optional;
import java.util.UUID;

/** 비밀번호 재설정(메일 요청·토큰 검증·교체)을 조율하는 서비스다. */
public interface PasswordResetProcessor {

    /** 가입 이메일이면 재설정 토큰을 보관하고 메일을 보낸다. 미가입 이메일은 조용히 무시한다(계정 존재 비노출). */
    void requestReset(String email);

    /**
     * 재설정 토큰을 소비해 새 비밀번호로 교체한다. 토큰은 1회용이다.
     *
     * @return 교체된 회원 ID. 무효 토큰(위조·만료·기사용)이면 빈 결과
     */
    Optional<UUID> reset(String token, String newPassword);
}

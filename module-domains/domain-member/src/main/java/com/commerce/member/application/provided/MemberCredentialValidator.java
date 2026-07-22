package com.commerce.member.application.provided;

import com.commerce.member.application.info.MemberInfo;
import com.commerce.member.domain.InvalidCredentialsException;

/** 회원 자격증명 검증을 담당하는 서비스다. */
public interface MemberCredentialValidator {

    /**
     * 이메일+패스워드 자격증명을 검증하고 검증된 회원을 반환한다. 정지 회원도 통과한다.
     *
     * @throws InvalidCredentialsException 미존재·탈퇴·패스워드 불일치 — 원인을 구분하지 않는다(계정 존재 노출 방지)
     */
    MemberInfo authenticate(String email, String rawPassword);
}

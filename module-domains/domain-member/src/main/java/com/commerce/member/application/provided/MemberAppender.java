package com.commerce.member.application.provided;

import com.commerce.member.domain.exception.DuplicateEmailException;
import com.commerce.member.domain.exception.InvalidEmailException;
import com.commerce.member.domain.exception.InvalidPasswordException;
import java.util.UUID;

/** 회원 가입을 담당하는 서비스다. */
public interface MemberAppender {

    /**
     * 구매자 회원을 가입시키고 새 회원 ID를 반환한다. 패스워드는 bcrypt 해시로만 저장한다(평문 비보관).
     *
     * @throws DuplicateEmailException 활성 회원 사이에서 이메일이 이미 쓰일 때
     * @throws InvalidPasswordException 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    UUID register(String email, String name, String rawPassword);

    /**
     * 관리자 회원을 가입시키고 새 회원 ID를 반환한다. 공개 가입 경로가 아니라 기동 시딩 전용이다.
     *
     * @throws DuplicateEmailException 활성 회원 사이에서 이메일이 이미 쓰일 때
     * @throws InvalidPasswordException 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     * @throws InvalidEmailException 이메일 형식이 올바르지 않으면
     */
    UUID registerAdmin(String email, String name, String rawPassword);
}

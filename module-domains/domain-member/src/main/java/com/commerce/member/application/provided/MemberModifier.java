package com.commerce.member.application.provided;

import com.commerce.member.domain.SuspensionReason;
import com.commerce.member.domain.exception.InvalidPasswordException;
import com.commerce.member.domain.exception.MemberNotFoundException;
import com.commerce.member.domain.exception.MemberStatusException;
import com.commerce.member.domain.exception.PasswordMismatchException;
import java.util.UUID;

/** 회원 상태 전이·표시 이름 변경·패스워드 교체를 담당하는 서비스다. */
public interface MemberModifier {

    /**
     * 회원을 정지한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws MemberStatusException 활성 상태가 아니면
     */
    void suspend(UUID memberId, SuspensionReason reason);

    /**
     * 회원 정지를 해제한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws MemberStatusException 정지 상태가 아니면
     */
    void reinstate(UUID memberId);

    /**
     * 회원 표시 이름을 바꾼다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    void rename(UUID memberId, String newName);

    /**
     * 현재 패스워드를 대조하고 새 패스워드로 교체한다. 새 패스워드는 bcrypt 해시로만 저장한다(평문 비보관).
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws PasswordMismatchException 현재 패스워드가 일치하지 않으면
     * @throws InvalidPasswordException 새 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     */
    void replacePassword(UUID memberId, String currentRawPassword, String newRawPassword);

    /**
     * 현재 패스워드 대조 없이 새 패스워드로 재설정한다. 재설정 토큰 검증을 마친 호출자만 부른다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     * @throws InvalidPasswordException 새 패스워드가 정책(8자 이상 72바이트 이하)에 어긋날 때
     */
    void resetPassword(UUID memberId, String newRawPassword);

    /**
     * 이메일 소유 인증을 기록한다.
     *
     * @throws MemberNotFoundException 활성 회원이 없으면
     */
    void verifyEmail(UUID memberId);
}

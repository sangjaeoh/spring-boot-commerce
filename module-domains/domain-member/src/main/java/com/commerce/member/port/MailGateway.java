package com.commerce.member.port;

/** 회원 메일 발송 포트다. 제목·본문 조립은 구현이 소유한다. */
public interface MailGateway {

    /** 비밀번호 재설정 토큰을 담은 메일을 보낸다. */
    void sendPasswordResetMail(String to, String token);

    /** 이메일 소유 인증 토큰을 담은 메일을 보낸다. */
    void sendVerificationMail(String to, String token);
}

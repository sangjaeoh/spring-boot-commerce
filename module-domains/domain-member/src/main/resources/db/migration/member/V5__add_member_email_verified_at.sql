-- 이메일 소유 인증 시각. 인증 토큰 검증이 채우고 미인증이면 NULL이다.
ALTER TABLE member.member
    ADD COLUMN email_verified_at TIMESTAMPTZ;

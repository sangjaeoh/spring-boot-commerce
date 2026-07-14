CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.member (
    id                UUID         NOT NULL,
    email             VARCHAR(320) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    suspension_reason VARCHAR(30),
    withdrawal_reason VARCHAR(30),
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_member PRIMARY KEY (id)
);

-- 활성 회원 사이 이메일 유니크. 탈퇴분(deleted_at IS NOT NULL)은 제외해 재가입을 허용한다.
CREATE UNIQUE INDEX ux_member_email_active ON member.member (email) WHERE deleted_at IS NULL;

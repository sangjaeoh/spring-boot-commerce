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
    password_hash     VARCHAR(60)  NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    -- 이메일 소유 인증 시각. 인증 토큰 검증이 채우고 미인증이면 NULL이다.
    email_verified_at TIMESTAMPTZ,
    CONSTRAINT pk_member PRIMARY KEY (id)
);

-- 활성 회원 사이 이메일 유니크. 탈퇴분(deleted_at IS NOT NULL)은 제외해 재가입을 허용한다.
CREATE UNIQUE INDEX ux_member_email_active ON member.member (email) WHERE deleted_at IS NULL;

CREATE TABLE member.member_address (
    id             UUID         NOT NULL,
    member_id      UUID         NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    zip_code       VARCHAR(20)  NOT NULL,
    road_address   VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255),
    phone          VARCHAR(30)  NOT NULL,
    is_default     BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_member_address PRIMARY KEY (id)
);

-- 회원별 배송지 조회용 논리 FK 인덱스.
CREATE INDEX ix_member_address_member_id ON member.member_address (member_id);

-- 회원당 기본 배송지 하나. 애플리케이션 불변식의 DB 백스톱이다.
CREATE UNIQUE INDEX ux_member_address_default ON member.member_address (member_id) WHERE is_default;

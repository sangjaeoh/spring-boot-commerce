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
    email_verified_at TIMESTAMPTZ,
    CONSTRAINT pk_member PRIMARY KEY (id)
);

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

CREATE INDEX ix_member_address_member_id ON member.member_address (member_id);

CREATE UNIQUE INDEX ux_member_address_default ON member.member_address (member_id) WHERE is_default;

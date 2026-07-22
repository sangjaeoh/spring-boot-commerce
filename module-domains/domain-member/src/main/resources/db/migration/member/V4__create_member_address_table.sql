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

CREATE SCHEMA IF NOT EXISTS coupon;

CREATE TABLE coupon.coupon (
    id               UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    discount_type    VARCHAR(10)  NOT NULL,
    discount_amount  BIGINT,
    discount_percent INTEGER,
    discount_max_cap BIGINT,
    min_order_amount BIGINT       NOT NULL,
    valid_from       TIMESTAMPTZ  NOT NULL,
    valid_until      TIMESTAMPTZ  NOT NULL,
    usage_valid_days INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    -- 발급 한도(선택)와 소진 카운트. 카운트는 원자적 조건부 UPDATE(issued_count < max_issuance)로만 증가한다.
    max_issuance     INTEGER,
    issued_count     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_coupon PRIMARY KEY (id)
);

CREATE TABLE coupon.issued_coupon (
    id            UUID        NOT NULL,
    coupon_id     UUID        NOT NULL,
    member_id     UUID        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    order_id      UUID,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    -- 관리자 무효화(REVOKED) 기록. status == REVOKED ⇔ revoked_at·revoke_reason 세팅.
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(255),
    CONSTRAINT pk_issued_coupon PRIMARY KEY (id)
);

-- 회원당 동일 쿠폰 1회 발급. 복합 유니크가 coupon_id 논리 FK 조회 인덱스를 겸한다.
CREATE UNIQUE INDEX ux_issued_coupon_coupon_member ON coupon.issued_coupon (coupon_id, member_id);
CREATE INDEX ix_issued_coupon_member_id ON coupon.issued_coupon (member_id);
CREATE INDEX ix_issued_coupon_order_id ON coupon.issued_coupon (order_id);

-- 정책별 발급분 목록(where coupon_id = ? order by created_at desc, id desc)의 필터·정렬을 인덱스로 해소한다.
-- 관리자 무효화 대상 발급분 발견이 도는 조회다. 위 ux_issued_coupon_coupon_member(coupon_id, member_id)는
-- coupon_id 필터는 겸하나 정렬(created_at·id)이 filesort로 남는다. 유니크 인덱스는 회원당 1회 발급 제약을 강제하는
-- 소유물이라 제거할 수 없어, 정렬까지 담는 복합 인덱스를 별도로 둔다(인기 정책은 발급분이 회원 수만큼 커진다).
CREATE INDEX ix_issued_coupon_coupon_id_created_at_id
    ON coupon.issued_coupon (coupon_id, created_at DESC, id DESC);

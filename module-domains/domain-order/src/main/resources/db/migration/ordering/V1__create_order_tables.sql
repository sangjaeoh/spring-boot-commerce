CREATE SCHEMA IF NOT EXISTS ordering;

CREATE TABLE ordering.orders (
    id                  UUID         NOT NULL,
    order_number        VARCHAR(40)  NOT NULL,
    member_id           UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    fulfillment_status  VARCHAR(20)  NOT NULL,
    total_amount        BIGINT       NOT NULL,
    discount_amount     BIGINT       NOT NULL,
    shipping_fee        BIGINT       NOT NULL,
    pay_amount          BIGINT       NOT NULL,
    issued_coupon_id    UUID,
    recipient_name      VARCHAR(100) NOT NULL,
    zip_code            VARCHAR(20)  NOT NULL,
    road_address        VARCHAR(255) NOT NULL,
    detail_address      VARCHAR(255),
    phone               VARCHAR(30)  NOT NULL,
    shipped_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(30),
    hold_reason         VARCHAR(30),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    refunded_at         TIMESTAMPTZ,
    refund_reason       VARCHAR(30),
    carrier             VARCHAR(100),
    tracking_number     VARCHAR(100),
    -- 취소·환불 전이가 재고·쿠폰 복원을 게이트하므로 동시 중복 전이를 낙관락으로 직렬화한다.
    version             BIGINT       NOT NULL DEFAULT 0,
    -- 체크아웃의 전 라인 재고 차감 완료 증거. 스윕·리컨실 보상의 재고 복원은 이 증거가 게이트한다(증거 없으면 복원 생략).
    stock_deducted_at   TIMESTAMPTZ,
    -- 취소 개시 마커. 취소 파사드가 PG 환불 앞에 커밋하고, 마커가 있는 동안 출고(ship)를 거부한다
    -- (환불 커밋과 주문 취소 전이 사이에 출고가 끼어들어 남는 환불 고아를 원천 차단).
    cancel_requested_at TIMESTAMPTZ,
    -- 반품 요청 축. 요청됨·거절·완료 상태와 요청 사유·시각을 보관하고, 승인(환불 완결) 시 COMPLETED로 닫는다.
    return_status       VARCHAR(30),
    return_requested_at TIMESTAMPTZ,
    return_reason       VARCHAR(30),
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_orders_order_number ON ordering.orders (order_number);
CREATE INDEX ix_orders_issued_coupon_id ON ordering.orders (issued_coupon_id);

-- 주문 목록 ID 페이지 쿼리(where member_id = ? order by created_at desc, id desc)의 필터·정렬을 인덱스로 해소한다.
-- 선두 컬럼(member_id)이 member_id 선행 조회(findByIdAndMemberId·
-- existsByMemberIdAndStatusAndFulfillmentStatusNot·위 페이지 쿼리)도 함께 대체하고, created_at·id를 인덱스
-- 정의와 같은 DESC로 두어 forward 스캔이 정렬을 만족한다.
CREATE INDEX ix_orders_member_id_created_at_id
    ON ordering.orders (member_id, created_at DESC, id DESC);

-- 관리자 주문 목록(where status = ? and fulfillment_status = ? order by created_at desc, id desc)의 필터·정렬을
-- 인덱스로 해소한다. 출고·환불 대상 발견(결제완료·미출고)이 도는 유일한 관리자 조회 경로다. 두 상태 컬럼을 선두에
-- 두어 필터를 좁히고, created_at·id를 인덱스 정의와 같은 DESC로 담아 인덱스 스캔 결과가 곧 정렬 결과다(forward
-- 스캔이 정렬을 만족해 filesort가 없다). orders는 최대 크기 테이블이라 인덱스 없이는 필터·정렬이 풀스캔이다.
CREATE INDEX ix_orders_status_fulfillment_created_at_id
    ON ordering.orders (status, fulfillment_status, created_at DESC, id DESC);

-- PENDING 주문 스윕(findByStatusAndCreatedAtBefore(PENDING, cutoff))이 주기적으로 도는 조회의 인덱스.
-- 스윕 대상은 항상 PENDING 단일 값이라 부분 인덱스로 좁힌다: PENDING은 체크아웃 진행 중에만 머무는 과도 상태라
-- 인덱스가 진행 중·잔존 주문만큼만 유지되고, 종결된 주문(PAID·CANCELLED·REFUNDED)은 상태 전이 시 인덱스에서
-- 자동 이탈한다 — 주문 누적과 무관하게 인덱스가 작게 남아 크기·쓰기 비용이 최소다(payment REQUESTED 부분
-- 인덱스와 같은 근거).
-- 스윕 유일 쿼리가 PENDING 고정이라 부분 인덱스의 커버리지 손실이 없어 부분 인덱스를 택한다(부분 인덱스는
-- status = 'PENDING' 리터럴을 아는 커스텀 플랜에서 매칭되며, 대안이 풀스캔이라 플래너가 커스텀 플랜을 유지한다).
CREATE INDEX ix_orders_status_created_at
    ON ordering.orders (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE ordering.order_line (
    id           UUID         NOT NULL,
    order_id     UUID         NOT NULL,
    variant_id   UUID         NOT NULL,
    product_id   UUID         NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    option_label VARCHAR(255),
    unit_price   BIGINT       NOT NULL,
    quantity     INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_order_line PRIMARY KEY (id)
);

CREATE INDEX ix_order_line_order_id ON ordering.order_line (order_id);
CREATE INDEX ix_order_line_variant_id ON ordering.order_line (variant_id);
CREATE INDEX ix_order_line_product_id ON ordering.order_line (product_id);

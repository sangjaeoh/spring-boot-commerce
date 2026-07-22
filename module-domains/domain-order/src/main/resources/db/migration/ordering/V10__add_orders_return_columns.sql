-- 반품 요청 축. 요청됨·거절·완료 상태와 요청 사유·시각을 보관하고, 승인(환불 완결) 시 COMPLETED로 닫는다.
ALTER TABLE ordering.orders
    ADD COLUMN return_status       VARCHAR(30),
    ADD COLUMN return_requested_at TIMESTAMPTZ,
    ADD COLUMN return_reason       VARCHAR(30);

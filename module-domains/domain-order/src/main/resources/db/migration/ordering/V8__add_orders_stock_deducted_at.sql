-- 체크아웃의 전 라인 재고 차감 완료 증거. 스윕·리컨실 보상의 재고 복원은 이 증거가 게이트한다(증거 없으면 복원 생략).
ALTER TABLE ordering.orders
    ADD COLUMN stock_deducted_at TIMESTAMPTZ;

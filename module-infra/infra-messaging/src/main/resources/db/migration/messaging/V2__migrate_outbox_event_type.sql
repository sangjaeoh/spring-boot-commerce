-- event_type을 이벤트 클래스 FQCN에서 논리 타입 키({도메인}.{이벤트명})로 전환한다.
-- 클래스 이동·개명이 잔존 행 해석에 영향을 주지 않도록 키를 코드 위치에서 분리한다.
UPDATE messaging.outbox
SET event_type = 'order.OrderPaid'
WHERE event_type = 'com.commerce.order.event.OrderPaid';

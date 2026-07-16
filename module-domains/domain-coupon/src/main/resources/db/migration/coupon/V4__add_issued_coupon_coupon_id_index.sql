-- 정책별 발급분 목록(where coupon_id = ? order by created_at desc, id desc)의 필터·정렬을 인덱스로 해소한다.
-- 관리자 무효화 대상 발급분 발견이 도는 조회다. 기존 ux_issued_coupon_coupon_member(coupon_id, member_id)는
-- coupon_id 필터는 겸하나 정렬(created_at·id)이 filesort로 남는다. 유니크 인덱스는 회원당 1회 발급 제약을 강제하는
-- 소유물이라 제거할 수 없어, 정렬까지 담는 복합 인덱스를 별도로 둔다(인기 정책은 발급분이 회원 수만큼 커진다).
CREATE INDEX ix_issued_coupon_coupon_id_created_at_id
    ON coupon.issued_coupon (coupon_id, created_at DESC, id DESC);

-- 자격증명(bcrypt 해시) 저장. 백필 없는 NOT NULL이므로 기존 행이 있는 DB에는 적용이 실패한다 —
-- 운용 전 연습 저장소라 기존 로컬 DB는 재생성한다(docker compose down -v 후 재기동·재마이그레이션).
ALTER TABLE member.member ADD COLUMN password_hash VARCHAR(60) NOT NULL;

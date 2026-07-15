-- 역할(BUYER/ADMIN) 도입. 기존 행은 전부 구매자로 백필하고, 이후 행은 앱이 역할을 명시하므로 기본값을 지운다.
ALTER TABLE member.member ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'BUYER';
ALTER TABLE member.member ALTER COLUMN role DROP DEFAULT;

# 문서 전수 동기화 설계

## 배경

코드는 worklog 19건(부분 취소, 라인 반품 워크플로, 재입고 알림, 위시리스트, 리뷰, 문의, 카테고리, 상품 이미지, 비밀번호 재설정·이메일 인증, 리프레시 토큰·로그아웃, 트랜잭션 아웃박스, 주소록, 검색·정렬, 체크아웃 미리보기, 바로구매, 선택구매, app-admin·app-batch 분리)을 모두 구현했으나 REQUIREMENTS.md·DOMAIN_MODEL.md·README.md가 이를 반영하지 않는다.

확인된 드리프트:

- REQUIREMENTS.md 제외 목록 대부분이 실제로는 구현됨. 제약·전제 절의 "아웃박스는 계속 범위 밖"은 `infra-messaging`의 `OutboxRelay` 실존과 정면 모순.
- REQUIREMENTS.md·DOMAIN_MODEL.md 공통의 "7개 도메인" 전제 vs 실제 도메인 모듈 10개 + shared.
- "관리자 전용 앱 분리 범위 밖" vs app-admin 실존. "리컨실 스윕은 app-api 내" vs app-batch로 이동.
- README.md mermaid에 존재하지 않는 `module-external`이 있고 `module-events`·`module-query`·app-admin·app-batch 누락.

전수 감사를 선택했으므로 확인된 지점 외 숨은 드리프트도 감사 대상이다.

## 목표·완료 기준

- REQUIREMENTS.md·DOMAIN_MODEL.md·README.md가 코드 현재 상태와 무모순이다.
- worklog/ 19개 파일을 삭제한다.
- 완료 검증: 재작성 후 독립 검증 에이전트가 문서 서술 표본을 코드와 대조해 모순 0건.

## 문서 역할

| 문서 | 역할 |
| --- | --- |
| REQUIREMENTS.md | 기획 요구사항 — 무엇을 만드는가 |
| DOMAIN_MODEL.md | 도메인 모델, 도메인 비즈니스 정책 |
| README.md | 이 레포의 설명 — 진입점, 실행법, 모듈 그래프 |

## 감사 단계 (병렬)

- 감사 단위 14개: 도메인 10개(member, product, stock, cart, coupon, order, payment, wishlist, review, inquiry) + 크로스컷 4개(인증·보안 / 결제 리컨실·스윕·웹훅 / 크로스도메인 조율·파사드 / 모듈·앱·인프라 구성).
- 각 감사 에이전트 입력: 담당 문서 섹션 발췌 + 담당 코드 영역. 산출: 사실표 — 문서 서술별 판정(정확 / 낡음 / 모순)과 실제 동작 요약.
- 문서에 서술이 아예 없는 신규 영역(wishlist, review, inquiry, category, 상품 이미지, 주소록, 재입고 알림 등)은 감사가 아니라 신규 서술 재료(기능 명세 초안)를 수집한다.

## 재작성 원칙

- 현재 상태로 서술한다. 편집 이력·"~였으나 이제" 서술을 넣지 않는다.
- 제외 목록은 감사로 확정된 진짜 제외만 남긴다(예: 실 PG 연동, 교환(exchange) 워크플로 — 코드에 없음 확인, 옵션 구조 정규화).
- 결정 근거 프로즈("~라 기각했다")는 여전히 참인 것만 보존한다.
- 문서 소유권을 지킨다: REQUIREMENTS는 기획 요구사항, DOMAIN_MODEL은 도메인 모델·비즈니스 정책, README는 레포 설명. 한 사실은 한 문서가 소유하고 나머지는 참조한다.
- README mermaid는 settings.gradle.kts의 실제 모듈 그래프로 재생성한다.
- 기존 문서의 톤·표 형식·한국어 프로즈 스타일을 그대로 따른다.

## 검증·커밋

- 재작성 후 검증 에이전트가 신문서 서술 표본을 코드와 대조한다. 모순 발견 시 수정하고 재검증한다.
- 커밋 4개로 분리한다:
  1. REQUIREMENTS.md 재작성
  2. DOMAIN_MODEL.md 재작성
  3. README.md 갱신
  4. worklog/ 삭제

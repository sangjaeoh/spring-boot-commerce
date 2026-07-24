# docs/project/ 3분할 설계 (REQUIREMENTS · DOMAIN_MODEL · decisions)

## 배경

REQUIREMENTS.md(기획 요구사항)와 DOMAIN_MODEL.md(도메인 모델·정책) 두 문서 사이 경계가 흐려져 있다.

- REQUIREMENTS.md `제약·전제` 절(243~264줄)이 ADR(Architecture Decision Record) 성격 — "~로 결정했다"/"~는 기각했다" 근거·트레이드오프 서술이 기획요구사항 문서에 섞여 있다.
- 같은 사실(배송지 최대 10개, 아웃박스 발행 메커니즘, 통화 KRW 단일 등)이 REQUIREMENTS.md와 DOMAIN_MODEL.md 양쪽에 중복 서술돼 있다.
- 2026-07-23 문서 전수 재작성(`docs/superpowers/specs/2026-07-23-doc-sync-design.md`) 때는 "결정 근거 프로즈는 참이면 보존한다" 원칙으로 이 형태를 의도적으로 만들었다 — 지금 이걸 재분리한다.

## 목표·완료 기준

- `docs/project/` 폴더 신설, REQUIREMENTS.md·DOMAIN_MODEL.md를 그 아래로 이동(git mv, 이력 보존).
- `docs/project/decisions.md` 신설 — ADR 형식으로 기술 결정 근거를 소유.
- REQUIREMENTS.md는 "무엇을·왜(비즈니스)"만 남긴다. ADR성 서술·중복 수치를 제거한다.
- DOMAIN_MODEL.md는 현행 유지 — 중복 수치의 단일 소유자로 확정되고, 서두에 decisions.md 참조 한 줄만 추가한다.
- README.md의 두 문서 링크 경로를 `docs/project/`로 갱신한다.
- 완료 검증: 세 문서 간 같은 사실의 재서술(숫자·메커니즘 이중 기술)이 0건, README 링크 정상.

## 범위 결정 (브레인스토밍 확정 사항)

- 작업 범위는 `docs/project/` 3개 파일로 한정한다 — 기존 `docs/architecture.md` 등 5(+3)개 규칙 문서는 건드리지 않는다. ADR의 Decision 항목 자체가 기술 사실의 서술 위치를 겸한다.
- decisions.md는 항목을 주제별로 묶는다(문장별 1:1 ADR화 금지) — 같은 근거를 공유하는 결정은 하나의 ADR로 합친다.
- 각 ADR은 정식 5섹션(Status·Context·Decision·Consequences·Alternatives Considered)을 쓴다.
- REQUIREMENTS.md·DOMAIN_MODEL.md에 중복된 구체 수치는 DOMAIN_MODEL.md가 유일 소유한다. REQUIREMENTS.md는 숫자를 지우고 서술만 남긴다(예: "배송지를 최대 10개까지" → "배송지를 등록 한도까지").

## 파일 이동

```
REQUIREMENTS.md   → docs/project/REQUIREMENTS.md   (git mv)
DOMAIN_MODEL.md    → docs/project/DOMAIN_MODEL.md    (git mv)
(신규)              → docs/project/decisions.md
```

- 두 문서는 서로 상대경로(`./DOMAIN_MODEL.md`, `./REQUIREMENTS.md`)로 참조하므로 함께 이동하면 상호 링크는 깨지지 않는다.
- README.md 117~118줄의 절대경로 링크(`DOMAIN_MODEL.md`, `REQUIREMENTS.md`)는 `docs/project/DOMAIN_MODEL.md`, `docs/project/REQUIREMENTS.md`로 갱신한다.

## 문서별 최종 역할

| 문서 | 역할 | 판별 테스트 |
|---|---|---|
| REQUIREMENTS.md | 기획 요구사항 — 무엇을·왜(비즈니스) | 이 줄을 지워도 코드 동작이 안 바뀌면 여기 소관 |
| DOMAIN_MODEL.md | 도메인 모델·비즈니스 정책, 필드 수준 구현 모델 | 코드로 바로 옮겨지는 스펙인가 |
| decisions.md | 기술 결정 근거·기각한 대안(ADR) | 이 결정을 뒤집어도 REQUIREMENTS·DOMAIN_MODEL 문구가 안 바뀌면 여기 소관 |

## REQUIREMENTS.md 변경 — 제거 대상 (DOMAIN_MODEL과 중복)

`제약·전제` 절에서 아래는 DOMAIN_MODEL.md가 이미 같은 내용을 문장 단위로 소유하므로 완전 삭제한다(별도 참조 문구도 남기지 않는다 — REQUIREMENTS.md 서두가 이미 "실현 모델은 DOMAIN_MODEL.md 소유"를 선언하므로 절마다 재확인이 불필요하다):

- 통화 KRW 단일 가정 (DOMAIN_MODEL 공통 규약 19줄과 중복)
- 크로스 트랜잭션 부분 실패 무손실 복구 범위 밖 (DOMAIN_MODEL "정합·보장 수준" 1134줄과 중복)
- 아웃박스 발행 메커니즘 상세(발행-커밋 원자, at-least-once, dead letter, 대상 이벤트 2종) (DOMAIN_MODEL "아웃박스" 절 1120~1128줄과 중복)
- 미확정 결제 리컨실·PENDING 스윕 담당 분리 설명 (DOMAIN_MODEL "미확정 결제 리컨실·웹훅 확정"·"PENDING 주문 스윕" 절과 중복)

## decisions.md — ADR 목록 (주제별 6묶음)

1. **Fake PG 설계와 한계 수용** — 동기 승인 fake, 트리거 금액 시뮬레이션, 재시작 시 거래 소실·다중 인스턴스 미도달 판정을 연습 한계로 수용.
2. **스케줄 스윕 동시성 제어(ShedLock 분산 락)** — 다중 인스턴스 전제, Redis 락, `lockAtMostFor`/`lockAtLeastFor` 판단, 단일 인스턴스 전제 기각 근거.
3. **헬스체크·Readiness 설계** — readiness에 DB·Redis 포함, liveness는 프로세스 생존만, compose bash `/dev/tcp` 채택 근거.
4. **Redis를 공유 인프라로 채택** — 멱등 저장소(SET NX, fail-closed, Postgres 대안 기각)·레이트리밋(IP 키, XFF 미신뢰, 이메일 키 기각, fail-closed)·리프레시 토큰·ShedLock 락 넷을 하나로 묶는다(같은 다중 인스턴스·fail-closed 근거를 공유 인용).
5. **시큐리티 응답 헤더 최소셋과 CORS 미설정** — nosniff·no-store 채택, HSTS·CSP·Referrer·Permissions-Policy 보류 근거, CORS 미설정 근거.
6. **인증·인가 아키텍처** — Spring Security 무상태 JWT 필터체인 채택(자체 필터 대안 기각), HS256 대칭키 app-api/app-admin 공유, 관리자 역할클레임 검사(요청마다 DB 조회 기각), 관리자 기동 시딩(마이그레이션 시딩 기각).

각 ADR 템플릿:

```markdown
### ADR-N: 제목

- Status: Accepted
- Context: (왜 이 결정이 필요했는가)
- Decision: (무엇을 택했는가 — 기술 사실 포함)
- Consequences: (이 결정이 남기는 트레이드오프·한계)
- Alternatives Considered: (기각한 대안과 기각 이유)
```

## DOMAIN_MODEL.md 변경

- 내용 변경 없음. 서두(6줄 부근)에 "기술 결정 근거는 [`decisions.md`](./decisions.md)가 소유한다" 한 줄만 추가.
- 중복 제거로 REQUIREMENTS.md 쪽 숫자가 사라지므로 이 문서가 해당 수치들의 유일 소유자가 된다(별도 변경 불필요, 이미 그렇게 쓰여 있음).

## 검증·커밋

- 재배치·재작성 후 세 문서를 훑어 같은 사실이 두 곳 이상에 재서술돼 있지 않은지 확인한다.
- README.md 링크가 실제 파일 위치와 일치하는지 확인한다.
- 커밋 분리:
  1. `docs/project/` 폴더 생성 + REQUIREMENTS.md·DOMAIN_MODEL.md git mv + README.md 링크 갱신
  2. REQUIREMENTS.md 제약·전제 절 정리(중복 제거·ADR성 서술 제거)
  3. `docs/project/decisions.md` 신설(ADR 6건)
  4. DOMAIN_MODEL.md 서두 참조 한 줄 추가

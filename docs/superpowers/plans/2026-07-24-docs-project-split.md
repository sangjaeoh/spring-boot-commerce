# docs/project/ 3분할 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** REQUIREMENTS.md·DOMAIN_MODEL.md를 `docs/project/`로 옮기고, ADR성 서술·중복 수치를 걷어내 `docs/project/decisions.md`로 분리한다.

**Architecture:** 문서 재배치(git mv) 1건, REQUIREMENTS.md 본문 편집 2건(제약·전제 정리, 기능요구사항 숫자 중복 제거), decisions.md 신규 작성 1건, DOMAIN_MODEL.md 참조 추가 1건, README.md 링크 갱신 1건. 순서 의존 있음 — 이동이 먼저다.

**Tech Stack:** 순수 마크다운 문서 편집. 코드 변경 없음, 빌드·테스트 불필요. 검증은 grep 기반 사실 대조.

## Global Constraints

- 작업 범위는 `docs/project/REQUIREMENTS.md`·`docs/project/DOMAIN_MODEL.md`·`docs/project/decisions.md`·`README.md` 4개 파일로 한정한다. `docs/architecture.md` 등 기존 5(+3)개 규칙 문서는 건드리지 않는다(설계 문서 `docs/superpowers/specs/2026-07-24-docs-project-split-design.md` 확정 사항).
- 파일 이동은 `git mv`로 이력을 보존한다.
- 각 태스크 종료 시 커밋한다(설계 문서의 4-커밋 분리안을 따른다).
- 편집 후에는 반드시 grep으로 "삭제했다고 생각한 문구가 실제로 남아있지 않은지", "새로 추가한 참조 링크가 가리키는 파일이 실제로 존재하는지"를 확인한다 — 이것이 이 작업의 "테스트"다.

---

## Task 1: `docs/project/` 생성, 파일 이동, README 링크 갱신

**Files:**
- Create: `docs/project/` (git mv 대상 디렉터리, 명시적 mkdir 불필요 — git mv가 생성)
- Move: `REQUIREMENTS.md` → `docs/project/REQUIREMENTS.md`
- Move: `DOMAIN_MODEL.md` → `docs/project/DOMAIN_MODEL.md`
- Modify: `README.md:117-118`

**Interfaces:**
- Produces: 이후 모든 태스크가 편집할 대상 경로 `docs/project/REQUIREMENTS.md`, `docs/project/DOMAIN_MODEL.md`.

- [ ] **Step 1: git mv로 두 파일 이동**

```bash
git mv REQUIREMENTS.md docs/project/REQUIREMENTS.md
git mv DOMAIN_MODEL.md docs/project/DOMAIN_MODEL.md
```

- [ ] **Step 2: 이동 확인**

Run: `git status --short`
Expected: 두 줄 모두 `R  ` (rename) 표시, `docs/project/REQUIREMENTS.md`·`docs/project/DOMAIN_MODEL.md`로 표시됨.

- [ ] **Step 3: 두 문서 간 상호 참조 확인(상대경로라 안 깨져야 함)**

Run: `grep -n "REQUIREMENTS.md\|DOMAIN_MODEL.md" docs/project/REQUIREMENTS.md docs/project/DOMAIN_MODEL.md`
Expected: `[`REQUIREMENTS.md`](./REQUIREMENTS.md)`, `[`DOMAIN_MODEL.md`](./DOMAIN_MODEL.md)` 형태의 상대경로 링크만 보임(절대경로·루트경로 없음).

- [ ] **Step 4: README.md 링크를 `docs/project/` 경로로 갱신**

`README.md:117-118`을 찾아 수정한다:

```markdown
| [`DOMAIN_MODEL.md`](docs/project/DOMAIN_MODEL.md) | 도메인 모델 |
| [`REQUIREMENTS.md`](docs/project/REQUIREMENTS.md) | 기능 요구사항 |
```

를 아래로 교체:

```markdown
| [`DOMAIN_MODEL.md`](docs/project/DOMAIN_MODEL.md) | 도메인 모델 |
| [`REQUIREMENTS.md`](docs/project/REQUIREMENTS.md) | 기능 요구사항 |
```

- [ ] **Step 5: 루트에 더 이상 참조가 안 남았는지 확인**

Run: `grep -rn "](REQUIREMENTS.md)\|](DOMAIN_MODEL.md)" --include="*.md" . 2>/dev/null | grep -v /build/`
Expected: 빈 결과(모든 링크가 `docs/project/` 접두로 갱신됨).

- [ ] **Step 6: 커밋**

```bash
git add -A README.md docs/project/REQUIREMENTS.md docs/project/DOMAIN_MODEL.md
git commit -m "docs: REQUIREMENTS.md·DOMAIN_MODEL.md를 docs/project/로 이동"
```

---

## Task 2: `docs/project/REQUIREMENTS.md` — 제약·전제 절 정리

DOMAIN_MODEL.md·decisions.md와 대조해 확인된 중복·ADR성 서술을 제거한다. 상세 판정 근거는 `docs/superpowers/specs/2026-07-24-docs-project-split-design.md`의 "REQUIREMENTS.md 변경 — 제거·정정 대상" 절 참조.

**Files:**
- Modify: `docs/project/REQUIREMENTS.md` (제약·전제 절 245~264줄 부근, 그리고 본문 forward-reference 3곳)

**Interfaces:**
- Consumes: Task 4에서 작성할 `decisions.md`의 ADR 제목(ADR-4·ADR-5·ADR-6) — forward-reference에 이 번호를 인용한다. Task 2를 Task 4보다 먼저 실행해도 무방하다(번호 체계는 이 계획서에서 이미 고정돼 있다).

- [ ] **Step 1: 제약·전제 절 전체를 아래 내용으로 교체**

`## 제약·전제` 헤더 다음 첫 불릿부터 그 섹션 끝(다음 `## ` 헤더 직전, 원래 245~264줄)까지를 통째로 아래로 교체한다:

```markdown
- 배포 단위는 앱 4개다 — app-api(구매자 셀프서비스 API)·app-admin(관리자 오퍼레이션·관리자 계정 기동 시딩)·app-batch(결제 리컨실·PENDING 스윕·아웃박스 릴레이·PG 웹훅 수신)·app-migration(전 도메인 스키마 마이그레이션 원샷). 네 앱은 도메인 모듈을 공유하되 프로세스를 분리한다(모듈 구조는 `docs/architecture.md` 소유).
- 미확정 결제 확정은 배치 앱(app-batch)의 `@Scheduled` 리컨실 스윕이 담당하고, PG 웹훅 수신도 같은 앱에서 같은 확정 경로를 공유한다. 확정 경로는 생성 후 유예(`payment.reconciliation.stale-after`)가 지난 결제만 손대 동기 체크아웃과 경합하지 않는다 — 유예 전 웹훅 통지는 무시되고 PG 재전달·주기 스윕이 수렴을 보장한다.
- 결제 요청 이전 중단으로 payment 행 없이 남은 PENDING 주문은 별도 `@Scheduled` 주문 기준 PENDING 스윕(app-batch)이 담당한다. 생성 후 유예(`order.reconciliation.stale-after`, 결제 리컨실 유예 이상)가 지난 주문 중 payment 행이 없는 주문만 직접 보상 종결한다(관할 판정 상세는 [`DOMAIN_MODEL.md`](./DOMAIN_MODEL.md) § PENDING 주문 스윕 소유).
- 스케줄 스윕 동시 실행 제어(ShedLock)·헬스체크·Readiness 설계·Redis 공유 인프라 채택 근거(멱등·레이트리밋·리프레시토큰·분산락)·시큐리티 응답 헤더·CORS·인증·인가 아키텍처(Spring Security 필터체인·JWT·관리자 가드·관리자 시딩)의 결정 배경과 기각한 대안은 [`decisions.md`](./decisions.md)가 소유한다.
- 통화 단일 가정·크로스 트랜잭션 부분 실패 복구 범위·아웃박스 발행 메커니즘은 [`DOMAIN_MODEL.md`](./DOMAIN_MODEL.md)가 소유한다.
- 관리자 쓰기·운영 조회 오퍼레이션(회원 지정 조회·이메일 검색·정지/해제, 상품·변형·이미지·카테고리 관리, 재고 관리, 쿠폰 정책·발급분 관리, 주문 출고·배송완료·보류/해제·반품 승인/거부·환불·상태 목록·검색, 문의 답변, 리뷰 삭제)은 전부 별도 Spring Boot 앱 app-admin의 `/api/v1/admin/**` 표면에 있고 도메인 Reader·Info 경계를 지킨다(강제 메커니즘은 decisions.md ADR-6 소유).
```

- [ ] **Step 2: 웹훅 인증 문장의 문서 내부 중복 제거**

원래 251줄이었던 "웹훅 인증은 PG와 공유한 시크릿(환경변수 `PAYMENT_WEBHOOK_SECRET`)의 HMAC-SHA256 본문 서명이다(토큰 인증 표면 밖)."는 Step 1에서 이미 삭제됐다(제약·전제 절 전체 교체에 포함). 같은 사실이 인증 절 80줄에 이미 있는지 확인만 한다.

Run: `grep -n "PAYMENT_WEBHOOK_SECRET" docs/project/REQUIREMENTS.md`
Expected: 결과가 정확히 1줄(80번 줄 부근, "PG 결제 확정 통지 웹훅은 X-Webhook-Signature 헤더에...").

- [ ] **Step 3: forward-reference 3곳 갱신**

`docs/project/REQUIREMENTS.md`에서 아래 3개 문자열을 찾아 교체한다.

66줄:
```
- 비밀번호 재설정 요청 표면에도 로그인·가입과 같은 IP당 레이트리밋이 걸려 있다(무인증 메일 발송 유발 표면 보호 — 키 설계·한도는 제약·전제).
```
→
```
- 비밀번호 재설정 요청 표면에도 로그인·가입과 같은 IP당 레이트리밋이 걸려 있다(무인증 메일 발송 유발 표면 보호 — 키 설계·한도 근거는 decisions.md ADR-4).
```

230줄(비기능요구사항 정합성 항목) 끝부분:
```
...결제 완료 후 장바구니 정리·재입고 알림 같은 이벤트 후처리는 트랜잭션 아웃박스로 발행돼 발행-커밋 사이 유실 창이 없고, 배치 릴레이가 최소 1회 전달하며 소비가 멱등이라 중복 전달을 흡수한다(전달 메커니즘·한계는 제약·전제).
```
→
```
...결제 완료 후 장바구니 정리·재입고 알림 같은 이벤트 후처리는 트랜잭션 아웃박스로 발행돼 발행-커밋 사이 유실 창이 없고, 배치 릴레이가 최소 1회 전달하며 소비가 멱등이라 중복 전달을 흡수한다(전달 메커니즘·한계는 DOMAIN_MODEL.md § 아웃박스 소유).
```

236줄(비기능요구사항 보안·자격 항목) 끝부분:
```
...로그인은 클라이언트 IP당 창 한도를 넘는 시도를 429로 거부해 무제한 브루트포스·크리덴셜 스터핑을 막고, 모든 응답에 시큐리티 헤더 최소셋(nosniff·no-store)을 싣는다(키 설계·창·한도·보류 헤더 근거는 제약·전제).
```
→
```
...로그인은 클라이언트 IP당 창 한도를 넘는 시도를 429로 거부해 무제한 브루트포스·크리덴셜 스터핑을 막고, 모든 응답에 시큐리티 헤더 최소셋(nosniff·no-store)을 싣는다(레이트리밋 키·창·한도 근거는 decisions.md ADR-4, 헤더 보류 근거는 ADR-5).
```

- [ ] **Step 4: 더 이상 "제약·전제" 자기참조(섹션 제목 제외)가 안 남았는지 확인**

Run: `grep -n "제약·전제" docs/project/REQUIREMENTS.md`
Expected: 딱 1줄 — `## 제약·전제` 섹션 헤더 자신만.

- [ ] **Step 5: 삭제 대상 문구가 실제로 사라졌는지 확인**

Run: `grep -n "ShedLock\|readiness 프로브\|liveness 프로브\|SET NX\|X-Forwarded-For\|HS256\|AUTH_JWT_SECRET" docs/project/REQUIREMENTS.md`
Expected: 빈 결과.

- [ ] **Step 6: 커밋**

```bash
git add docs/project/REQUIREMENTS.md
git commit -m "docs: REQUIREMENTS.md 제약·전제 절에서 ADR성 서술·중복 제거"
```

---

## Task 3: `docs/project/REQUIREMENTS.md` — 기능요구사항 숫자 중복 제거

**Files:**
- Modify: `docs/project/REQUIREMENTS.md` (41·42·56·94·162·171줄)

**Interfaces:**
- Consumes: 없음(Task 2와 독립적인 편집 대상 — 겹치는 줄 없음).

- [ ] **Step 1: 6곳 순차 교체**

| 위치 | 이전 | 이후 |
|---|---|---|
| 42줄 | `배송지는 회원당 최대 10개까지 등록할 수 있다.` | `배송지는 회원당 등록 한도까지 등록할 수 있다.` |
| 41줄 | `새 비밀번호는 가입과 같은 정책(8자 이상 72바이트 이하)을 따르고` | `새 비밀번호는 가입과 같은 패스워드 정책을 따르고` |
| 56줄 | `패스워드는 8자 이상 72바이트(bcrypt 입력 한계) 이하다.` | `패스워드는 정책이 정한 길이 범위(bcrypt 입력 한계 이내)를 따른다.` |
| 94줄 | `jpeg·png·webp만 지원하고 장당 5MB를 넘을 수 없다.` | `jpeg·png·webp만 지원하고 장당 용량 상한을 넘을 수 없다.` |
| 162줄 | `별점(1~5)·본문(1~1000자) 리뷰를 쓸 수 있다.` | `별점·본문(정책 범위 내) 리뷰를 쓸 수 있다.` |
| 171줄 | `상품에 문의를 남길 수 있다(본문 1~1000자, 공백 불가).` | `상품에 문의를 남길 수 있다(본문은 정책 범위 내, 공백 불가).` |

- [ ] **Step 2: 남은 숫자 중복이 없는지 재확인**

Run: `for p in "최대 10개" "8자 이상 72바이트" "5MB" "1~5" "1~1000자"; do echo "=== $p ==="; grep -n "$p" docs/project/REQUIREMENTS.md; done`
Expected: 전부 빈 결과.

- [ ] **Step 3: 같은 값이 DOMAIN_MODEL.md 쪽엔 그대로 남아있는지 확인(유일 소유자 확정)**

Run: `for p in "최대 10개" "8자 이상 72바이트" "5MB" "1~5" "1~1000자"; do echo "=== $p ==="; grep -c "$p" docs/project/DOMAIN_MODEL.md; done`
Expected: 전부 1 이상.

- [ ] **Step 4: 커밋**

```bash
git add docs/project/REQUIREMENTS.md
git commit -m "docs: REQUIREMENTS.md 기능요구사항의 DOMAIN_MODEL 중복 수치 제거"
```

---

## Task 4: `docs/project/decisions.md` 신규 작성

**Files:**
- Create: `docs/project/decisions.md`

**Interfaces:**
- Consumes: Task 2에서 REQUIREMENTS.md 제약·전제 절에서 삭제한 원문(ShedLock·헬스체크·Redis·시큐리티헤더·CORS·인증인가 문단) — 아래 Step 1 본문에 이미 반영돼 있다.
- Produces: ADR-1~ADR-6 제목·번호. Task 2의 forward-reference("decisions.md ADR-4" 등)가 가리키는 대상이 이 번호와 정확히 일치해야 한다.

- [ ] **Step 1: 파일 작성**

```markdown
# 기술 결정 기록 (Decisions)

이 문서는 이 앱의 기술 결정 근거와 기각한 대안을 소유한다. "무엇을"은 [`REQUIREMENTS.md`](./REQUIREMENTS.md), "실현 모델"은 [`DOMAIN_MODEL.md`](./DOMAIN_MODEL.md)가 소유한다 — 이 문서는 그 둘의 특정 선택이 "왜" 그렇게 됐는지, 무엇을 기각했는지만 담는다. 이 문서의 판정 결정을 뒤집어도 REQUIREMENTS.md·DOMAIN_MODEL.md의 서술 자체는 바뀌지 않아야 한다(바뀐다면 그 내용은 이 문서가 아니라 그 두 문서가 소유해야 한다).

각 항목은 Status·Context·Decision·Consequences·Alternatives Considered 5섹션을 쓴다. 현재 전부 Status: Accepted다(Superseded 이력 없음).

## ADR-1: Fake PG 설계와 한계 수용

- Status: Accepted
- Context: 실 PG 연동 없이 결제 흐름 전체(승인·실패·환불·리컨실)를 오프라인으로 결정론적으로 검증할 수 있어야 했다.
- Decision: 결제 게이트웨이는 동기 승인 fake다 — 거래를 인메모리로 보관하고 트리거 금액으로 거절·응답 유실을 결정론적으로 시뮬레이션한다.
- Consequences: 프로세스 재시작 시 PG측 거래가 소실된다(실 PG는 지속). 다중 인스턴스 배포에서 승인 노드 밖의 상태 조회가 미도달로 판정될 수 있다(실 PG는 공유 시스템이라 없는 문제 — 확정 경로가 다른 노드의 유실 승인을 청구 미도달 실패로 잘못 종결할 수 있다). 둘 다 연습 한계로 수용한다.
- Alternatives Considered: 실 PG 샌드박스 연동 — 외부 계약·네트워크 의존이 생겨 오프라인 검증 용이성을 해친다는 이유로 기각.

## ADR-2: 스케줄 스윕 동시성 제어 — ShedLock 분산 락

- Status: Accepted
- Context: 결제 리컨실·PENDING 주문 스윕은 다중 인스턴스 배포를 전제한다. 같은 스윕이 여러 노드에서 동시에 돌면 PG 조회 중복·경합 경고 노이즈가 생긴다.
- Decision: ShedLock 분산 락(Redis)으로 노드 간 겹치는 동시 실행을 배제한다. 확정 경로가 멱등이라 중복 실행도 정합성은 깨지지 않으므로 락은 정합성 장치가 아니라 노이즈 제거 목적이다. 비정상 종료(SIGKILL 등)로 남은 락은 `lockAtMostFor`(10분) 만료로 회수되며 그동안 전 노드가 해당 스윕을 건너뛴다. 웹훅 확정 경로는 락 밖이라 스윕과의 동시 확정이 남지만 멱등이 흡수한다.
- Consequences: 노드별 위상이 어긋난 주기 실행은 락으로 줄지 않되, 이미 종결된 대상을 재조회하지 않는 빈 스캔이라 비용은 낮다. 락 경합은 조용한 건너뜀이고 Redis 장애 시엔 주기마다 오류 로그와 함께 스윕이 걸러지되 다음 주기가 재시도한다(Redis는 이미 멱등 저장소의 경성 의존이라 새 장애 축이 아니다).
- Alternatives Considered: 단일 인스턴스 전제 선언(락 불필요) — 멱등·레이트리밋 저장소의 Redis 채택 근거(다중 인스턴스)와 모순돼 기각. `lockAtLeastFor`로 주기당 1회로 더 제한 — 빈 스캔 비용이 이미 낮아 과한 장치라 기각.

## ADR-3: 헬스체크·Readiness 설계

- Status: Accepted
- Context: 컨테이너·오케스트레이터가 세 서비스 앱(app-api·app-admin·app-batch)의 상태를 판단해야 한다.
- Decision: readiness 프로브(`/actuator/health/readiness`)의 readiness 그룹에 DB·Redis를 포함한다 — 서비스 준비 = 앱 기동 + 저장소 도달로 정의한다. liveness 프로브는 프로세스 생존만 보고 DB·Redis를 넣지 않는다. compose는 JRE 베이스에 curl/wget이 없어 bash `/dev/tcp` 직접 HTTP GET으로 readiness를 확인한다.
- Consequences: 헬스·메트릭 엔드포인트는 무인증이라 인그레스가 `/actuator/**`를 외부로 라우팅하지 않는 내부망 노출을 전제한다.
- Alternatives Considered: 리슨 여부만 보는 체크 — 저장소 불능을 healthy로 오판해 기각. liveness에 DB·Redis 포함 — 일시적 저장소 장애로 컨테이너가 재기동되면 복구가 오히려 늦어져 기각.

## ADR-4: Redis를 공유 인프라로 채택 — 멱등·레이트리밋·리프레시토큰·분산락

- Status: Accepted
- Context: 멱등 필터(더블서밋 방어)·로그인/가입/재설정 레이트리밋·리프레시 토큰 저장·ShedLock 분산 락 넷 다 다중 인스턴스 배포에서 공유 상태가 필요하다.
- Decision: 넷 다 Redis를 쓴다. 멱등 키는 `SET NX` 원자 선점, 만료는 TTL(AOF 활성화로 재시작 내구성 확보). 레이트리밋은 클라이언트 IP(소켓 원격 주소) 키, 창당 한도 IP당 10회/5분 고정창(원자 `INCR`+`PEXPIRE` Lua), 표면별 키 접두사(`login:`·`signup:`·`reset:`)로 카운터를 분리해 한 표면의 시도가 다른 표면 한도를 소모하지 않는다. 프록시 헤더(`X-Forwarded-For`)는 알려진 프록시가 없어 신뢰하지 않는다(위조 가능). 멱등·레이트리밋 둘 다 Redis 장애 시 fail-closed(거부)한다 — 중복 차단 없는 통과는 이중 결제 위험이라 가용성보다 정합성을 택했다.
- Consequences: Redis가 이미 돈의 경로(멱등)의 경성 의존이므로 레이트리밋·리프레시토큰·ShedLock을 얹어도 새 장애 축이 아니다. 공유 NAT 부수 차단·분산 출처 우회는 IP 단일 키의 한계로 수용하고 프런트엔드·프록시 도입 시 재결정한다. 계정 잠금·captcha·2FA는 범위 밖이다(레이트리밋만).
- Alternatives Considered: 멱등 키를 Postgres에 저장 — TTL 만료 처리를 직접 구현해야 해 기각(만료가 규칙인 dedup 락은 네이티브 TTL이 맞다). 레이트리밋 키를 이메일 기준·조합 키로 — 본문 파싱이 필요하고 이메일 회전으로 우회되어 기각, 현 단계는 IP 단일 키를 택했다.

## ADR-5: 시큐리티 응답 헤더 최소셋과 CORS 미설정

- Status: Accepted
- Context: 브라우저 클라이언트가 없는 JSON API 범위에서 어떤 보안 헤더를 실을지, CORS를 어떻게 설정할지 정해야 했다.
- Decision: 모든 응답에 `X-Content-Type-Options: nosniff`(MIME 스니핑 차단)·`Cache-Control: no-store`(토큰을 싣는 로그인 응답 포함 캐싱 차단) 최소셋을 자체 필터로 싣는다. CORS는 미설정을 유지한다 — 브라우저 교차 출처 요청 불허가 기본이다.
- Consequences: HSTS는 TLS 종단이 앱 밖이라, CSP·`X-Frame-Options` 계열은 브라우저 문서를 제공하지 않는 JSON API라, Referrer·Permissions-Policy는 브라우저 클라이언트가 없어 보류한다. 캐시 전략 도입 시 캐시 가능한 조회를 no-store에서 옵트아웃해야 한다. 프런트엔드 도입 시 CORS 허용 출처·메서드·헤더·자격 동반을 재결정해야 한다.
- Alternatives Considered: 전체 보안 헤더 표준셋 선제 적용 — 브라우저 클라이언트가 없는 현재 범위에서 근거 없는 헤더(CSP 등)를 넣는 게 의미 없어 기각.

## ADR-6: 인증·인가 아키텍처

- Status: Accepted
- Context: 무상태 API에 로그인·토큰 검증·역할 기반 접근 제어를 붙여야 했다.
- Decision: Spring Security 무상태 JWT 필터 체인을 쓴다 — 세션·CSRF·폼로그인·HTTP Basic을 끄고(`STATELESS`) 토큰 검증 필터를 `UsernamePasswordAuthenticationFilter` 앞에 둔다. 미인증 익명은 진입점 401, 권한 부족은 접근거부 핸들러 403을 problem+json으로 낸다. JWT는 HS256 단일 대칭 키로 서명하고 키는 환경변수(`AUTH_JWT_SECRET`)로 주입한다. app-api·app-admin 두 앱이 같은 키를 공유해 app-api가 발급한 토큰을 app-admin이 검증만 한다(app-admin에는 로그인·가입 표면이 없다). 관리자 가드는 토큰의 역할 클레임만 검사하고 요청마다 회원 저장소를 조회하지 않는다. 관리자 시딩은 마이그레이션이 아니라 설정 기반 기동 시딩이다(app-admin 전용). 관리자 전용 마커는 클래스 레벨 `@Admin`(ADMIN 역할 검사)이며 시큐리티 체인의 경로 인가와 이중 계층으로 강제한다.
- Consequences: 키 로테이션·JWKS는 범위 밖이다. 역할 변경 반영은 새 토큰 발급부터이며 반영 지연은 토큰 TTL로 바운드된다. 패스워드 해시는 spring-security-crypto(bcrypt)를 쓴다.
- Alternatives Considered: 자체 강제 필터·인터셉터로 인가 구현 — 이 범위를 자체 코드로도 덮을 수 있었으나 URL 인가·진입점/접근거부 계약을 표준 필터 체인에 위임하는 게 나아 Spring Security로 결정. 요청마다 회원 저장소에서 역할 조회 — 변경 즉시 반영이 장점이나 가드가 도메인에 결합되고 요청마다 조회 비용이 들어 기각(역할 변경 수단이 시딩뿐이라 반영 지연은 토큰 TTL로 바운드된다). 마이그레이션 시딩 — 자격증명 해시가 스키마 이력에 고정되고 환경별 자격증명 분리가 불가능해 기각.
```

- [ ] **Step 2: ADR 번호와 REQUIREMENTS.md forward-reference 일치 확인**

Run: `grep -n "^## ADR-" docs/project/decisions.md`
Expected: ADR-1부터 ADR-6까지 순서대로 6개.

Run: `grep -n "ADR-[0-9]" docs/project/REQUIREMENTS.md`
Expected: ADR-4·ADR-5·ADR-6 인용이 decisions.md에 실제 존재하는 번호와 일치.

- [ ] **Step 3: 커밋**

```bash
git add docs/project/decisions.md
git commit -m "docs: decisions.md 신설 — ADR 6건(Fake PG·ShedLock·헬스체크·Redis·시큐리티헤더·인증인가)"
```

---

## Task 5: `docs/project/DOMAIN_MODEL.md` — decisions.md 참조 추가

**Files:**
- Modify: `docs/project/DOMAIN_MODEL.md:6-7` 부근

**Interfaces:**
- Consumes: 없음(Task 4의 decisions.md 존재만 전제).

- [ ] **Step 1: 서두에 참조 한 줄 추가**

`docs/project/DOMAIN_MODEL.md`의 아래 부분(원래 6~7줄):

```markdown
- 설계 규칙(ID·연관·상태 전이·소프트삭제 등)은 `docs/architecture.md`·`docs/entity-persistence.md`·`docs/coding-conventions.md`를 따른다. 이 문서는 그 규칙을 이 앱의 10개 도메인에 적용한 결과다.
- 모듈 구조·빌드 순서 같은 "어떻게 세우는가"는 이 문서의 범위가 아니다(아키텍처 규칙 문서 `docs/`가 소유).
```

다음 줄에 삽입:

```markdown
- 기술 결정의 근거·기각한 대안은 [`decisions.md`](./decisions.md)가 소유한다. 이 문서는 결정된 모델만 서술하고 "왜"는 재서술하지 않는다.
```

- [ ] **Step 2: 반영 확인**

Run: `grep -n "decisions.md" docs/project/DOMAIN_MODEL.md`
Expected: 방금 추가한 1줄.

- [ ] **Step 3: 커밋**

```bash
git add docs/project/DOMAIN_MODEL.md
git commit -m "docs: DOMAIN_MODEL.md에 decisions.md 참조 추가"
```

---

## Task 6: 전수 검증

**Files:** 없음(읽기 전용 검증)

- [ ] **Step 1: 세 문서 간 숫자 재중복 스캔**

Run:
```bash
for p in "최대 10개" "8자 이상 72바이트" "5MB" "1~5" "1~1000자" "KRW 단일"; do
  echo "=== $p ==="
  echo "REQUIREMENTS: $(grep -c "$p" docs/project/REQUIREMENTS.md)"
  echo "DOMAIN_MODEL: $(grep -c "$p" docs/project/DOMAIN_MODEL.md)"
done
```
Expected: REQUIREMENTS 전부 0(단 "KRW 단일"은 REQUIREMENTS에 원래 없었으므로 0 유지), DOMAIN_MODEL 전부 1 이상.

- [ ] **Step 2: README 링크 파일 존재 확인**

Run: `test -f docs/project/REQUIREMENTS.md && test -f docs/project/DOMAIN_MODEL.md && test -f docs/project/decisions.md && echo OK`
Expected: `OK`

- [ ] **Step 3: 루트에 옛 파일이 안 남았는지 확인**

Run: `test -f REQUIREMENTS.md && echo "STALE" || echo "CLEAN"`
Run: `test -f DOMAIN_MODEL.md && echo "STALE" || echo "CLEAN"`
Expected: 둘 다 `CLEAN`.

- [ ] **Step 4: git 이력 보존 확인**

Run: `git log --follow --oneline -- docs/project/REQUIREMENTS.md | tail -5`
Expected: 이동 전 REQUIREMENTS.md 커밋 이력(예: 2026-07-23 doc-sync 커밋)이 그대로 보임.

- [ ] **Step 5: 최종 커밋 로그 확인**

Run: `git log --oneline -8`
Expected: Task 1~5의 커밋 5개가 순서대로 보임(설계 문서 커밋 2개 포함 총 7개 이상).

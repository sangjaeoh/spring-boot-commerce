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

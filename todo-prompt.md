# TODO 작업 요청 프롬프트

[`todo.md`](./todo.md)의 각 항목을 새 세션에서 작업 요청할 때 쓰는 프롬프트다. 항목 하나를 골라 코드 블록 안 내용을 그대로 붙여넣는다. 프롬프트는 자기완결이라 이 파일의 다른 부분 없이 단독으로 동작한다(레포 규칙은 CLAUDE.md→AGENTS.md가 자동 적용). 각 프롬프트는 마지막에 todo.md 체크 갱신 단계를 포함한다. 항목 번호 순서가 곧 권장 실행 순서이며 순서 근거는 todo.md가 소유한다.

각 프롬프트의 엔드포인트 URL·요청 형상은 제안이다 — 기존 컨트롤러 관례(액션형 POST 서브리소스, problem+json 오류 매핑)와 충돌하면 관례를 따른다. 구현이 REQUIREMENTS.md·DOMAIN_MODEL.md의 선언과 충돌하는 항목은 해당 선언의 갱신이 각 작업의 일부이며, 문서 편집은 AGENTS.md 편집 규약을 따른다.

## 1. CI 파이프라인

```text
[작업] GitHub Actions 빌드 게이트 추가

배경(확인된 사실):
- .github 디렉터리가 없다. 품질 게이트(Spotless·NullAway·Error Prone·ArchUnit·전체 테스트)는 로컬 ./gradlew build에만 존재한다.
- 이 저장소는 git 원격(remote)이 없다 — GitHub 리포 생성·푸시가 선행된다. 공개 리포면 GitHub 호스티드 표준 러너가 무료이고, 비공개여도 Free 플랜 월 2,000분 무료에 기본 지출 한도 $0라 초과 시 실행이 멈출 뿐 과금되지 않는다.
- Java 25 toolchain(build-logic convention), 통합 테스트는 Testcontainers가 PostgreSQL(postgres:17-alpine)·Redis를 직접 띄운다 — CI 러너에 Docker가 필요하다(GitHub Actions ubuntu 러너는 내장).

목표: main 푸시와 PR에서 ./gradlew build가 원격으로 강제되고, 실패가 눈에 보인다.

작업 내용:
1. 선행: GitHub 리포 생성·원격 등록·푸시. 공개/비공개는 사용자에게 확인한다(공개 권장 근거: Actions 무료). 이미 원격이 있으면 건너뛴다.
2. .github/workflows/build.yml: push(main)·pull_request 트리거 → JDK 25 셋업(temurin 등 배포판의 25 지원 확인) → Gradle 캐시(공식 setup-gradle 액션) → ./gradlew build.
3. 분 소모 제어를 워크플로에 포함한다: concurrency(같은 ref의 이전 실행 cancel-in-progress), timeout-minutes(행 방지).
4. Testcontainers가 러너 Docker로 도는지 확인한다(추가 서비스 선언 불필요 — 테스트가 직접 컨테이너를 띄운다).
5. 병합 차단(branch protection)은 리포 설정이라 코드 밖 — README 또는 PR 설명에 권장 설정만 언급한다.
6. 검증: 워크플로를 실제로 1회 통과시킨다(그린 런 확인 없이 끝내지 않는다).

하지 말 것: 배포·릴리스 자동화, 매트릭스 빌드, 커버리지 리포트 연동(todo.md 의도적 제외 — CI 정착 후 별도 결정).

완료 기준: 워크플로 그린 런 1회 확인, 로컬 ./gradlew build 통과.
완료 후: 루트 todo.md의 1번 항목(CI 파이프라인)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 2. 조회 경로 인덱스 보강

```text
[작업] 조회 경로 인덱스 보강 — 리컨실 스윕·주문 목록

배경(확인된 사실):
- payment 테이블 인덱스는 PK와 ux_payment_order_id뿐이다(payment 스키마 V1). 리컨실 스윕이 1분 주기(payment.reconciliation.fixed-delay)로 PaymentRepository.findByStatusAndCreatedAtBefore(REQUESTED, cutoff)를 실행하므로 payment 누적 시 매분 풀스캔이 된다.
- 주문 목록 ID 페이지 쿼리는 where member_id ... order by created_at desc, id desc인데 인덱스는 단일 컬럼 ix_orders_member_id뿐이라 정렬이 filesort다(ordering 스키마 V1).
- 스키마는 도메인별 Flyway·1변경 1파일 규칙이다(docs/architecture.md). ddl-auto=validate는 인덱스를 검증하지 않으므로(docs/entity-persistence.md) 인덱스 누락·오기는 어떤 게이트도 잡지 않는다 — 마이그레이션 SQL 자체가 산출물이고 검증은 EXPLAIN이다.

목표: 리컨실 스윕과 주문 목록 조회가 인덱스를 타고, 그 사실이 EXPLAIN으로 확인된다.

작업 내용:
1. payment 스키마 V2: REQUESTED 스윕용 인덱스. 부분 인덱스((status, created_at) WHERE status = 'REQUESTED') vs 일반 복합 인덱스의 트레이드오프를 명시하고 결정한다(스윕 대상이 항상 REQUESTED 단일 값이라 부분 인덱스 제안 — 크기·쓰기 비용 최소, 확정된 결제가 인덱스에서 자동 이탈).
2. ordering 스키마 V4: (member_id, created_at DESC, id DESC) 복합 인덱스. 기존 ix_orders_member_id의 제거 여부를 판단하고 근거를 남긴다(복합 인덱스가 선두 컬럼 조회를 대체).
3. 검증: 로컬 compose Postgres에서 두 쿼리의 EXPLAIN으로 인덱스 사용을 확인하고 결과를 PR 설명에 남긴다. app-migration 실행·validate 기동 통과.

하지 말 것: 다른 스키마 인덱스 일괄 정비(대상 두 쿼리만), 쿼리 형상 변경, 리포지토리 코드 수정(SQL만).

완료 기준: EXPLAIN 확인 기록이 남고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 2번 항목(조회 경로 인덱스 보강)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 3. 관리자 조회 표면

```text
[작업] 관리자 조회 표면 추가 — 운영 오퍼레이션의 대상 발견 경로

배경(확인된 사실):
- 관리자 쓰기 오퍼레이션은 26개가 완결돼 있으나 관리자 조회는 사실상 0개다. 모든 관리자 액션이 경로의 대상 ID를 전제하는데 그 ID를 열거할 API가 없다:
  (a) 주문 상태 기준 목록 없음 — GET /api/v1/orders는 본인(토큰 주체) 목록뿐이고 OrderRepository에 status 기준 조회가 없다. 출고 대상(결제완료·미출고) 주문을 찾을 수 없어 ship·refund가 운영 불가.
  (b) 회원 검색 없음 — MemberReader는 getMember(id)뿐. findByEmailAndDeletedAtIsNull은 로그인·가입 내부 전용이라 미노출.
  (c) 쿠폰 정책 목록 없음 — CouponReader가 아예 없다(service에 CouponAppender·CouponModifier만). 만든 정책을 다시 볼 수 없다.
  (d) 정책별 발급분 목록 없음 — IssuedCouponRepository에 couponId 기준 조회가 없어 revoke 대상 발급분 ID를 찾을 수 없다.
  (e) 재고 현황 조회 없음 — StockController는 쓰기 전용. StockReader.getByVariantId(s)는 있으나 미노출.
  (f) 숨김 포함 상품 목록 없음 — 목록 조회는 ProductReader.getExposedPage(노출분)뿐.
- docs/architecture.md는 app-admin(격리 구역 infrastructure/query의 크로스 스키마 조회)을 규정하지만 실제 앱은 app-api·app-migration 2종이다. 반면 REQUIREMENTS.md 제외 목록은 "관리자 전용 앱 분리"를 선언한다 — 배치 결정이 이 선언과 얽힌다.
- 관리자 가드는 @AdminOnly(AdminOnlyInterceptor), 페이지네이션 기성 패턴은 상품·주문 목록(page/size/totalElements/totalPages)이다.

목표: 관리자가 API만으로 운영 대상(출고할 주문·정지할 회원·무효화할 발급분·재입고할 재고·쿠폰 정책·숨김 상품)을 찾을 수 있다.

작업 내용:
1. 배치 결정 먼저(트레이드오프 명시): app-admin 신설(architecture.md 형상 실현 — infrastructure/query 격리 구역·크로스 스키마 조회 허용, 기동 앱·compose·문서 추가 비용) vs app-api에 관리자 조회 추가(REQUIREMENTS 제외 선언 유지, Reader·Info 경계 준수). 최소 변경은 후자다 — 다른 선택이면 근거를 남기고, 결정이 REQUIREMENTS.md "관리자 전용 앱 분리" 선언과 충돌하면 선언을 같이 갱신한다.
2. 조회 여섯을 최소 형상으로 추가한다(전부 @AdminOnly, 목록은 기성 페이지 규약):
   - 주문: 상태 필터 목록. 기존 본인 목록(GET /orders)과 표면이 겹치지 않게 경로를 결정한다. 최소 필터는 결제 상태·이행 상태.
   - 회원: 이메일 정확 일치 검색(단건 조회 GET /{memberId}는 기존 활용).
   - 쿠폰: 정책 목록(CouponReader 신설) + 정책별 발급분 목록(couponId 기준 조회 + 페이지).
   - 재고: 변형 ID(들) 기준 현황 조회 노출.
   - 상품: 숨김 포함 관리자 목록(기존 getExposedPage와 별개 페이지 조회).
3. 신규 조회의 인덱스 필요 여부를 각각 판단한다(주문 상태 목록·발급분 couponId 등 — 필요 시 해당 스키마 마이그레이션 포함, 1변경 1파일).
4. 테스트: 각 조회의 관리자 성공·구매자 403·미인증 401 대표 케이스, 페이지 규약, 숨김 상품 포함 여부.

하지 말 것: 복합 검색·정렬 옵션·집계 대시보드, 회원 전체 나열(이메일 검색으로 충분한지 판단), 관리자 계정 관리 API, CSV 내보내기.

완료 기준: 위 여섯 공백이 닫히고 테스트·./gradlew build 게이트가 통과하며, 배치 결정과 문서 선언이 정합하다.
완료 후: 루트 todo.md의 3번 항목(관리자 조회 표면)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 4. PENDING 주문 스윕(주문 기준 리컨실)

```text
[작업] PENDING 주문 스윕 — 결제 행 없는 미결제 주문의 보상 종결

배경(확인된 사실):
- 체크아웃은 주문 생성(PENDING) → 재고 차감 → 쿠폰 사용 → 결제 요청·승인 → markPaid를 한 HTTP 호출에서 동기 수행한다(CheckoutFacade.checkout). 주문 커밋이 payment 행 생성보다 앞서므로, 주문 생성 이후~결제 요청 이전 구간에서 프로세스가 크래시하면 PENDING 주문 + 차감 재고 + 사용 쿠폰이 남되 payment 행이 없다.
- 기존 리컨실(PaymentConfirmationFacade.reconcileStaleRequested)은 payment REQUESTED 행만 스윕하므로 이 주문을 영원히 발견하지 못한다. PENDING 스윕 배치도 없다 — 유일하게 자동 복구가 없는 무한 잔존 경로다(차감 재고가 묶이는 팬텀 품절).
- 재고 복원은 가산이라 멱등이 아니다(Stock.restore). 주문 CANCELLED 1회성 전이를 정확히-1회 보상의 가드로 쓰는 기성 패턴이 OrderCancellationFacade에 있다(전이 성공 쪽만 복원 수행).
- payment 행이 있는 PENDING(결제 요청 후 크래시)은 기존 리컨실이 확정·보상한다(confirmFailure의 PENDING 분기 포함) — 관할이 겹치면 이중 개입 위험이 있다.
- 탈퇴 회원의 PENDING 주문도 같은 경로로 잔존한다(탈퇴는 PENDING 주문을 막지 않는다).

목표: 유예가 지난 PENDING 주문을 주기 스윕이 발견해 보상 종결(재고·쿠폰 복원 + 주문 취소)하고, 반복 실행·기존 리컨실과의 경합에서도 복원이 정확히 한 번이다.

작업 내용:
1. 관할 설계 먼저(트레이드오프 명시): 스윕이 payment 행 존재를 확인해 — 있으면 기존 결제 리컨실에 위임(건너뜀), 없으면 직접 보상. 유예(제안: 결제 리컨실 stale-after 이상)를 두어 진행 중 체크아웃과 경합하지 않게 한다. 기존 리컨실의 PENDING 분기와 규칙이 겹치는 지점을 명시한다.
2. 보상 순서는 기성 패턴을 따른다: 주문 CANCELLED 1회성 전이 선행(성공한 쪽만 복원 수행) → 쿠폰 복원(멱등) → 재고 복원(가산). 스윕 처리 건은 경고 로그를 남긴다(잔존 발생의 관측 수단).
3. 스윕 쿼리(상태+생성시각) 추가 + ordering 스키마 인덱스(부분 인덱스 (status, created_at) WHERE status = 'PENDING' 제안 — 2번 항목과 같은 근거) 마이그레이션.
4. @Scheduled 주기·유예 프로퍼티는 결제 리컨실 관례(payment.reconciliation.*)를 따라 명명·배치한다.
5. 테스트: 유예 전 미개입, payment 행 없는 PENDING 보상(재고·쿠폰 복원·주문 CANCELLED), payment 행 있는 PENDING 미개입(기존 리컨실 관할), 반복 실행 멱등(복원 정확히 1회), 탈퇴 회원 주문도 종결.

하지 말 것: 취소·환불 보상 중 크래시가 남기는 "재고 미복원 CANCELLED"의 자동 복구(정상 종결 건과 판별할 근거가 없다 — 계속 범위 밖), 아웃박스, 사용자 개시 미결제 취소 API.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, REQUIREMENTS.md 정합성 서술(리컨실 수렴 보장)이 PENDING 스윕을 포함하게 갱신돼 있다.
완료 후: 루트 todo.md의 4번 항목(PENDING 주문 스윕)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 5. 보안 하드닝

```text
[작업] 보안 하드닝 — 로그인 레이트리밋·시큐리티 헤더·CORS 정책

배경(확인된 사실):
- AuthController.login에 시도 횟수 제한·레이트리밋이 전무하다. 로그인 실패는 원인 무구분 401(계정 존재 비노출)이지만 무제한 시도가 가능해 브루트포스·크리덴셜 스터핑에 열려 있다.
- CORS 설정이 코드·yml 어디에도 없다(미설정 = 브라우저 교차 출처 차단이 기본이라 현재는 안전하나, 정책이 결정된 적이 없다).
- 시큐리티 응답 헤더가 없다. Spring Security는 의도적 미도입(REQUIREMENTS.md 제약·전제 — 얇은 자체 토큰 코드)이라 프레임워크 기본 헤더도 없다.
- Redis가 이미 인프라에 있다(멱등 저장소 infra-redis, SET NX 선점·fail-closed). 멱등 저장소는 다중 인스턴스를 이유로 Redis를 택했다.

목표: 로그인 무제한 시도가 차단되고(초과 시 429), 시큐리티 헤더 최소셋이 응답에 실리며, CORS 정책이 결정으로 기록된다.

작업 내용:
1. 로그인 레이트리밋(트레이드오프 명시): 저장소는 Redis 제안(멱등 저장소와 같은 다중 인스턴스 근거·기성 인프라). 키 설계(이메일 기준 vs IP 기준 vs 조합)와 창·한도(예: 키당 N회/M분)를 결정하고 근거를 남긴다. 초과는 429 problem+json. Redis 장애 시 동작은 멱등 필터의 fail-closed 결정과의 일관성을 검토해 결정한다(fail-open은 방어 무력화, fail-closed는 로그인 불능 — 트레이드오프 명시).
2. 시큐리티 헤더: 자체 필터(기존 common-web 필터 체계 관례)로 최소셋을 결정해 부착한다 — JSON API 서버 전제에서 가치 있는 것만(제안: X-Content-Type-Options: nosniff, 인증 응답 Cache-Control. HSTS는 TLS 종단 부재, CSP·frame 계열은 브라우저 문서 미제공이라 보류 — 각 보류 근거를 남긴다).
3. CORS: 브라우저 클라이언트가 없는 현 단계의 정책을 결정으로 기록한다(제안: 미설정 유지 = 교차 출처 불허를 REQUIREMENTS.md 제약·전제에 선언, 프런트엔드 도입 시 재결정).
4. 테스트: 연속 로그인 실패 후 429, 창 경과 후 재시도 가능, 응답 헤더 존재. 기존 로그인 테스트 회귀 통과.

하지 말 것: Spring Security 전면 도입(기각 결정 유지), 계정 잠금·captcha·2FA, 전 엔드포인트 글로벌 레이트리밋(로그인 표면만).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, REQUIREMENTS.md 보안 서술이 새 방어선을 포함한다.
완료 후: 루트 todo.md의 5번 항목(보안 하드닝)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 6. 런타임·컨테이너 하드닝

```text
[작업] 런타임·컨테이너 하드닝 — graceful shutdown·커넥션 풀·compose·non-root

배경(확인된 사실):
- server.shutdown 미설정 → Boot 기본 immediate. 배포 재시작 시 진행 중 요청(체크아웃 등 돈의 경로 포함)이 끊긴다.
- HikariCP 설정이 전무해 풀 크기·타임아웃이 전부 기본값이다. Tomcat server.* 블록도 없다.
- docker-compose에 restart 정책이 없고(전 서비스) 리소스 제한도 없다. Dockerfile이 non-root USER 없이 root로 앱을 실행한다.
- 프로필은 default(운영 성격)·local 2종이다. compose api 헬스체크는 bash /dev/tcp 방식이다.

목표: 재시작·장애 시 동작이 우연이 아니라 의도된 설정이 된다 — 진행 중 요청을 마치고 내려가고, 풀·타임아웃이 명시값이며, 컨테이너가 non-root로 돌고 죽으면 다시 뜬다.

작업 내용:
1. graceful shutdown: server.shutdown=graceful + spring.lifecycle.timeout-per-shutdown-phase(값 근거 명시). 셧다운 중 실행 중인 @Scheduled 스윕과의 관계를 확인한다.
2. HikariCP: maximum-pool-size·connection-timeout·max-lifetime 명시(Postgres max_connections·앱 인스턴스 수 전제와 함께 근거 서술). Tomcat 스레드는 기본값 수용 여부를 판단만 하고 근거를 남긴다.
3. compose: postgres·redis·api에 restart 정책(unless-stopped 제안, migration 원샷은 제외). 리소스 제한은 운영 환경 미정이라 보류 근거만 남긴다.
4. Dockerfile: non-root USER 추가(두 타깃 공통). 기존 compose 헬스체크가 계속 동작하는지 확인한다.
5. 검증: 클린 compose 풀스택 기동 → README 스모크 성공, 컨테이너 프로세스 uid 확인, api 컨테이너 kill 후 자동 재기동 확인.

하지 말 것: k8s 매니페스트, 오토스케일·리소스 튜닝 정교화, 프로필 체계 개편.

완료 기준: 검증 절차가 성공하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 6번 항목(런타임·컨테이너 하드닝)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 7. 재고 동시성 테스트

```text
[작업] 재고 동시성 테스트 — "오버셀 없음"의 경합 검증

배경(확인된 사실):
- "동시 주문에도 오버셀 없음"은 REQUIREMENTS.md 비기능 요구인데, 이를 경합으로 검증하는 테스트가 없다. StockPersistenceTest는 단일 스레드 @Version 증가만 확인하고, 멀티스레드 동시성 테스트는 쿠폰 한도(CouponIssuanceFacadeTest.concurrentIssuanceNeverExceedsLimit — ExecutorService+CountDownLatch)뿐이다.
- 재고 차감은 낙관락이다: Stock @Version, deduct는 로드→수량 가드→감산이고 StockModifier.deduct에 재시도가 없다. 경합 충돌은 ObjectOptimisticLockingFailureException → ProblemDetailHandler가 409 CONCURRENT_MODIFICATION으로 매핑한다.
- 체크아웃 경합 실패 쪽은 동기 보상(재고·쿠폰 복원·주문 취소)이 이미 구현·테스트돼 있다(순차 시나리오).

목표: 동시 체크아웃 경합에서 오버셀이 없고 실패 쪽 보상이 완결됨을 멀티스레드 테스트가 증명하며, 낙관락 충돌의 표면 동작(재시도 없이 409)의 유지 여부가 결정으로 남는다.

작업 내용:
1. 동시성 테스트(쿠폰 한도 테스트의 기성 하네스 패턴): 재고 M, 동시 체크아웃 N(>M) — 성공 주문 수 ≤ M, 최종 재고 = M − 성공 수(0 이상), 실패 주문은 CANCELLED이고 쿠폰·재고 복원 완결. 단일 변형 경합을 기본으로 하고 복수 라인 케이스 추가 여부는 판단한다.
2. 표면 동작 결정(트레이드오프 명시): 현행 유지(충돌 409 — 클라이언트 재시도 규약, 멱등 필터와 정합) vs 파사드/서비스 제한 재시도(가용성 개선, 재시도 중 보상 복잡도 증가). 제안: 현행 유지 + 결정 근거 기록. 재시도를 도입하기로 하면 범위를 차감 지점 하나로 한정한다.
3. 경합 충돌이 409로 매핑되는 웹 통합 회귀 1개.

하지 말 것: 비관락·조건부 UPDATE로 재고 모델 재설계(현 모델의 검증이 목적 — 재설계는 테스트가 결함을 드러낼 때 별도 결정), 부하 테스트 도구 도입.

완료 기준: 경합 테스트가 플레이키 없이 반복 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 7번 항목(재고 동시성 테스트)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 8. 스케줄러 분산 락

```text
[작업] 스케줄러 분산 락 — 착수 여부 재평가 후 도입 또는 보류 종결

배경(확인된 사실):
- @Scheduled 잡은 결제 리컨실 스윕(PaymentConfirmationFacade.reconcileStaleRequested, 1분 fixed-delay)이고, 4번 항목 완료 시 PENDING 주문 스윕이 추가된다. ShedLock 등 분산 락은 전무하다.
- 다중 인스턴스 배포 시 전 노드가 동시에 같은 스윕을 실행한다. 확정 경로가 상태 가드로 멱등이라 정합성은 깨지지 않으나, PG 조회 중복과 경합 경고 로그 노이즈가 생긴다.
- 멱등 저장소는 다중 인스턴스를 근거로 Redis를 채택했다(REQUIREMENTS.md 제약·전제) — 배포 전제에 관한 기존 결정과의 일관성이 재평가 기준이다. Redis가 이미 있어 도입 비용은 낮다.
- 착수 전 정직한 재평가가 조건이다: 단일 인스턴스 전제를 선언하고 보류로 종결하는 것도 유효한 완료다(아웃박스 항목 선례).

목표: "다중 인스턴스에서 스윕이 몇 번 도는가"가 우연이 아니라 결정이 된다 — 도입하거나, 단일 인스턴스 전제를 선언하고 보류를 종결한다.

작업 내용:
1. 재평가: 이 연습의 배포 전제(단일 vs 다중 인스턴스)를 확정한다. 멱등 저장소의 Redis 채택 근거(다중 인스턴스)와 모순되지 않게 결정하고 근거를 남긴다.
2-a. 도입 시: ShedLock + Redis LockProvider 최소 구성 — 두 스윕에 @SchedulerLock, lockAtMostFor는 스윕 소요 상한 근거와 함께. 락 획득 실패는 정상 건너뜀.
2-b. 보류 시: REQUIREMENTS.md 제약·전제에 "스케줄 스윕은 단일 인스턴스 실행을 전제한다(확정 경로가 멱등이라 중복 실행도 정합성 무해)"를 선언하고, todo.md 항목에 재평가 결과를 기록해 종결한다.
3. 도입 시 테스트: 동시 두 실행 중 한쪽만 스윕을 수행하는 락 검증 통합 테스트 1개.

하지 말 것: 쿼츠·배치 프레임워크 도입, 리더 선출 일반화, 락 대상을 스윕 외로 확장.

완료 기준: 결정과 근거가 문서에 남고(도입 시 테스트·./gradlew build 통과), todo.md가 갱신된다.
완료 후: 루트 todo.md의 8번 항목(스케줄러 분산 락)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 9. 쿠폰 할인 미리보기

```text
[작업] 쿠폰 할인 미리보기 조회 — 체크아웃 전 예상 할인액

배경(확인된 사실):
- 할인 계산은 체크아웃 내부(CheckoutFacade의 쿠폰 할인 산출)에서만 일어나 구매자가 체크아웃 전에 할인액을 알 수 없다. 도메인에는 IssuedCouponReader.calculateDiscount가 이미 있으나 어떤 엔드포인트도 노출하지 않는다.
- 발급분 조회에는 "미소유는 미존재(404) 취급" 관례가 있다(IssuedCouponReader.getIssuedCoupon(id, memberId)).
- 적용 조건(ISSUED·본인·사용 기한 내·최소 주문 금액·산출 할인 > 0)은 도메인이 소유하고, 장바구니 총액 조회(GET /carts의 total)는 이미 있다.

목표: 본인 발급분에 대해 주문금액을 넣으면 예상 할인액이 나온다 — 클라이언트는 장바구니 총액과 조합해 체크아웃 전에 할인을 표시할 수 있다.

작업 내용:
1. 형상 결정: 상태를 바꾸지 않는 조회이므로 GET 제안(예: GET /api/v1/issued-coupons/{issuedCouponId}/discount-preview?orderAmount=). 기존 액션형 POST 관례는 상태 변경용이라 GET이 정합 — 다르게 판단하면 근거를 남긴다.
2. 적용 불가(만료·최소 주문 금액 미달·이미 사용·무효화)의 응답 표현을 결정한다(제안: 오류가 아니라 적용 가능 여부+사유+0원 — 미리보기는 실패가 아니라 정보다. 다른 선택이면 근거 명시). 미소유는 기존 관례대로 404.
3. 미리보기는 계산만 하고 상태를 바꾸지 않는다(발급분 잠금·선점 없음 — 체크아웃 시점 재검증이 진실).
4. 테스트: 정액·정률(상한 포함) 계산, 최소 주문 금액 미달·만료 표현, 미소유 404, 미리보기 후 상태 불변.

하지 말 것: 장바구니 자동 연동(orderAmount 파라미터로 충분), 최적 쿠폰 추천, 미리보기 결과 보증(체크아웃 시점 재계산이 진실).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 9번 항목(쿠폰 할인 미리보기)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 10. 회원 사유 파라미터 검증 일관화

```text
[작업] 회원 사유 파라미터 검증 일관화 — @RequestParam을 본문 DTO로 통일

배경(확인된 사실):
- 사유를 받는 액션 중 MemberController의 suspend(@RequestParam SuspensionReason)·withdraw(@RequestParam WithdrawalReason)만 쿼리 파라미터다. 다른 사유 액션은 전부 @Valid @RequestBody DTO다: OrderRefundRequest(@NotNull RefundReason)·FulfillmentHoldRequest(@NotNull HoldReason)·IssuedCouponRevocationRequest(@NotBlank String).
- 그 결과 오류 응답 형상이 갈린다: 본문 DTO는 VALIDATION_FAILED + 필드 errors 구조인데, 파라미터 부재·enum 오기는 구조화 errors 없는 400이다.
- 외부 클라이언트 부재 전제의 breaking change 선례가 있다(이전 단계 11번 — 주문 목록 List 형상 제거).

목표: 사유를 받는 모든 액션이 같은 형상(@Valid 본문 DTO + @NotNull enum)과 같은 오류 응답 구조를 갖는다.

작업 내용:
1. suspend·withdraw 요청 본문 DTO 신설(기성 사유 DTO의 네이밍·패턴), 컨트롤러 시그니처 교체. 쿼리 파라미터 형상은 제거한다(병행 유지 금지 — 외부 클라이언트 부재 전제).
2. 기존 웹 통합 테스트 갱신 + 사유 누락 시 VALIDATION_FAILED 구조 응답 회귀 추가.
3. README 스모크·OpenAPI 문서에 영향이 있으면 함께 갱신한다.

하지 말 것: 다른 컨트롤러 형상 리팩터(사유 두 액션만), 사유 체계 확장.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 10번 항목(회원 사유 파라미터 검증 일관화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

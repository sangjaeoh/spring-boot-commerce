# TODO 작업 요청 프롬프트

[`todo.md`](./todo.md)의 각 항목을 새 세션에서 작업 요청할 때 쓰는 프롬프트다. 항목 하나를 골라 코드 블록 안 내용을 그대로 붙여넣는다. 프롬프트는 자기완결이라 이 파일의 다른 부분 없이 단독으로 동작한다(레포 규칙은 CLAUDE.md→AGENTS.md가 자동 적용). 각 프롬프트는 마지막에 todo.md 체크 갱신 단계를 포함한다. 항목 번호 순서가 곧 권장 실행 순서이며 순서 근거는 todo.md가 소유한다.

각 프롬프트의 엔드포인트 URL·요청 형상은 제안이다 — 기존 컨트롤러 관례(액션형 POST 서브리소스, problem+json 오류 매핑)와 충돌하면 관례를 따른다. 이 단계 항목 다수는 REQUIREMENTS.md·DOMAIN_MODEL.md가 "제외"·"향후 확장"으로 선언한 것을 구현한다 — 해당 선언의 갱신(무엇이 들어오고 무엇이 계속 밖인지)이 각 작업의 일부이며, 문서 편집은 AGENTS.md 편집 규약을 따른다.

## 1. CI 파이프라인

```text
[작업] GitHub Actions 빌드 게이트 추가

배경(확인된 사실):
- .github 디렉터리가 없다. 품질 게이트(Spotless·NullAway·Error Prone·ArchUnit·전체 테스트)는 로컬 ./gradlew build에만 존재한다.
- Java 25 toolchain(build-logic convention), 통합 테스트는 Testcontainers가 PostgreSQL(postgres:17-alpine)을 직접 띄운다 — CI 러너에 Docker가 필요하다(GitHub Actions ubuntu 러너는 내장).

목표: main 푸시와 PR에서 ./gradlew build가 원격으로 강제되고, 실패가 눈에 보인다.

작업 내용:
1. .github/workflows/build.yml: push(main)·pull_request 트리거 → JDK 25 셋업(temurin 등 배포판의 25 지원 확인) → Gradle 캐시(공식 setup-gradle 액션) → ./gradlew build.
2. Testcontainers가 러너 Docker로 도는지 확인한다(추가 서비스 선언 불필요 — 테스트가 직접 컨테이너를 띄운다).
3. 병합 차단(branch protection)은 리포 설정이라 코드 밖 — README 또는 PR 설명에 권장 설정만 언급한다.
4. 검증: 워크플로를 실제로 1회 통과시킨다(그린 런 확인 없이 끝내지 않는다).

하지 말 것: 배포·릴리스 자동화, 매트릭스 빌드, 커버리지 리포트 연동(요청 밖).

완료 기준: 워크플로 그린 런 1회 확인, 로컬 ./gradlew build 통과.
완료 후: 루트 todo.md의 1번 항목(CI 파이프라인)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 2. 회원 자격증명·로그인 API

```text
[작업] 회원 자격증명(패스워드) + 로그인·토큰 발급 API 추가

배경(확인된 사실):
- 인증 코드가 전무하다: Spring Security 의존성이 버전 카탈로그·전 모듈 build.gradle.kts에 없고, 컨트롤러 Javadoc들이 "인증이 범위 밖이라 소유권은 검사하지 않는다"고 반복 명시한다.
- Member는 이메일(Email 값객체·불변)·이름·상태(ACTIVE↔SUSPENDED)·논리삭제만 보유하고 자격증명 저장이 없다.
- REQUIREMENTS.md "제외"와 DOMAIN_MODEL.md "명시적 범위 밖"이 인증·로그인·회원 자격증명을 선언하고 있다 — 이 작업은 그 선언을 바꾸는 결정이다.
- 스키마는 도메인별 Flyway(member 스키마, 현재 V1)로 관리된다.

목표: 가입이 패스워드를 받고, POST /api/v1/auth/login이 이메일+패스워드로 액세스 토큰(JWT)을 발급하며, 후속 항목(3·4번)이 이 토큰에서 주체·역할을 도출할 수 있다.

작업 내용:
1. 범위 선언 갱신 먼저: 두 문서에서 인증을 범위로 옮기되 새 경계를 명시한다(토큰 로그인만 포함, 소셜 로그인·리프레시 토큰·비밀번호 재설정·이메일 인증은 계속 밖).
2. 설계 결정을 명시하고 하나를 골라 근거를 남긴다: Spring Security 도입 vs 얇은 자체 필터. 패스워드 해시 알고리즘(bcrypt 등). JWT 서명 키 관리(최소: 환경변수 주입).
3. member 도메인: 패스워드 해시 저장 + member 스키마 V2 마이그레이션. 기존 행 처리(NOT NULL 여부·연습 DB 재생성 허용 여부)를 판단해 명시한다. 가입 오퍼레이션·API에 패스워드 추가.
4. POST /api/v1/auth/login — 성공 시 토큰 응답, 실패 시 401 problem+json. 탈퇴·정지 회원의 로그인 허용 여부를 결정하고 근거를 남긴다(제안: 탈퇴는 거부, 정지는 로그인 허용 — 차단은 기존 도메인 게이트가 담당).
5. 토큰 검증 필터는 이 항목에서 준비만 하고 기존 엔드포인트에 강제하지 않는다(강제는 3번 항목) — 한 번에 한 층.
6. 테스트: 가입→로그인→토큰 클레임 검증, 잘못된 패스워드 401, 탈퇴 회원 로그인 거부, 해시 저장(평문 미저장) 확인.

하지 말 것: 소셜 로그인·리프레시 토큰·비밀번호 재설정, 기존 엔드포인트에 인증 강제(3번 소관), 역할 도입(4번 소관).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트(Spotless·NullAway·Error Prone·ArchUnit)가 통과하며, 두 문서의 범위 선언이 갱신돼 있다.
완료 후: 루트 todo.md의 2번 항목(회원 자격증명·로그인 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 3. 인증 주체 강제

```text
[작업] 구매자 셀프서비스 API의 자기 신고 memberId 제거 + 토큰 주체·소유권 강제

배경(확인된 사실):
- memberId가 세 방식으로 자기 신고된다: 경로변수(MemberController /{memberId}), 쿼리파라미터(카트 조회·삭제·비우기, GET /api/v1/orders, 발급쿠폰 단건·목록), 요청 본문(카트 담기·수량 변경, CheckoutRequest, CouponIssuanceRequest). 서버는 소유권을 전혀 검증하지 않는다(컨트롤러 Javadoc 명시).
- 2번 항목(로그인·토큰 발급·검증 필터)이 선행돼 있어야 한다.
- 발급쿠폰 단건 조회에는 "미소유는 미존재 취급" 관례가 이미 있다.

목표: 구매자 셀프서비스 API가 토큰 주체에서 memberId를 도출하고, 자기 신고 memberId 파라미터가 표면에서 사라지며, 미인증 요청은 401·타인 리소스 접근은 거부된다.

작업 내용:
1. 표면 분류 먼저: 전체 엔드포인트를 본인용(카트 5개, 체크아웃, 주문 목록·상세·취소, 발급쿠폰 2개, 회원 본인 조회·이름변경·탈퇴)·관리자용(4번에서 가드)·공개(카탈로그 목록·상세 — 비로그인 쇼핑 허용 제안)로 나눠 표로 남긴다. 회원 조회(GET /{memberId})처럼 본인·관리자 겸용 후보는 판단을 명시한다.
2. 토큰 검증 필터를 배선하고 주체 memberId를 컨트롤러에 주입한다(아규먼트 리졸버 등 — 2번에서 고른 인증 방식의 관례를 따른다). 본인용 엔드포인트의 요청 DTO·쿼리파라미터·경로변수에서 memberId를 제거한다.
3. 소유권 검사: 주문 상세·취소는 주체와 주문 소유자 일치를 확인한다. 거부 표현(404 미존재 취급 vs 403)은 발급쿠폰의 기존 관례와 일관되게 결정하고 근거를 남긴다.
4. 기존 컨트롤러·파사드·웹 통합 테스트를 토큰 발급 헬퍼 기반으로 갱신하고, 미인증 401·타인 주문 접근 거부 회귀를 추가한다.

하지 말 것: 관리자 오퍼레이션 가드(4번 소관 — 이 항목에서는 형상만 유지), 인가 프레임워크 과설계, 요청 밖 엔드포인트 신설.

완료 기준: 본인용 표면에서 자기 신고 memberId가 사라지고, 위 테스트가 통과하며 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 3번 항목(인증 주체 강제)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 4. 관리자 역할 분리

```text
[작업] 역할(BUYER/ADMIN) 도입 + 관리자 오퍼레이션 가드

배경(확인된 사실):
- 관리자 성격의 오퍼레이션이 구매자와 같은 표면에 무가드 노출돼 있다: 회원 정지·해제, 상품 등록·show/hide·편집·삭제, 변형 추가·가격·enable/disable/retire, 재고 increase·mark-sold-out·mark-sellable·discontinue, 쿠폰 정책 생성·disable/enable·발급, 주문 ship·delivery-confirmation·fulfillment-hold/release.
- Member에 역할 개념이 없다. 2·3번 항목(토큰 주체)이 선행돼 있어야 한다.

목표: 관리자 토큰만 관리자 오퍼레이션을 호출할 수 있고, 구매자 토큰은 403, 공개 표면은 무인증으로 동작한다.

작업 내용:
1. 역할 모델 결정: member.role(BUYER/ADMIN) 필드 vs 별도 관리자 계정 체계 — 트레이드오프를 명시하고 최소안을 고른다(제안: role 필드 + member 스키마 마이그레이션 + 관리자 시딩 방법 결정 — 마이그레이션 시딩 vs 로컬 프로필 시딩).
2. 3번에서 만든 표면 분류표를 완성한다(관리자/본인/공개 3분류). 쿠폰 발급의 주체를 결정한다: 관리자가 특정 회원에게 발급(현재 본문 memberId 형상에 가까움) vs 회원 셀프 클레임 — 하나를 골라 형상을 정리한다.
3. 관리자 가드를 적용한다(2번에서 고른 인증 방식의 관례 — Security라면 authorization rule, 자체 필터라면 인터셉터/리졸버). 구매자 토큰 403, 미인증 401.
4. 테스트: 관리자 성공·구매자 403 대표 케이스(도메인별 1개 이상), 공개 표면(카탈로그) 무인증 접근, 역할 시딩 검증.

하지 말 것: 세분화된 퍼미션·권한 매트릭스, 관리자 전용 앱 분리, 관리자 계정 관리 API(시딩으로 충분).

완료 기준: 표면 분류표가 문서 또는 테스트로 남고, 위 테스트가 통과하며 ./gradlew build 게이트가 통과한다. REQUIREMENTS.md의 인증 범위 서술과 모순이 없다.
완료 후: 루트 todo.md의 4번 항목(관리자 역할 분리)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 5. 결제 현실화

```text
[작업] 실패 가능한 PG + PENDING 리컨실 + 웹훅 확정 경로

배경(확인된 사실):
- 프로덕션 PG는 StubPaymentGateway(module-external/external-payment) 하나이며 모든 승인을 무조건 성공 처리한다("STUB-APPROVE-"+UUID). 실패 경로는 app-api 테스트의 더블로만 검증된다(CheckoutFaultCompensationTest).
- PaymentGateway 포트는 approve(Money, PaymentMethod)/cancel(pgTransactionId, idempotencyKey)뿐이고 상태 조회가 없다.
- PaymentProcessor는 PG 호출을 트랜잭션 밖에서 하고 영속 실패 시 환불로 역보상한다. Payment는 REQUESTED→APPROVED(→CANCELLED) 상태와 failureReason·pgTransactionId·pgCancelTransactionId를 보유한다.
- REQUIREMENTS.md "향후 확장" 1항이 "실 PG·비동기 웹훅 및 미결제 리컨실"을 선언한다.
- CheckoutFacade가 체크아웃에서 결제 요청·승인을 동기 조율하고, 승인 실패 시 재고·쿠폰 복원 보상이 이미 구현·테스트돼 있다.

목표: 결제가 실패·지연·응답 유실을 겪어도 돈과 주문 상태가 어긋나지 않는다 — 실패 가능한 PG 위에서 기존 보상이 동작하고, 응답이 유실된 REQUESTED 결제는 리컨실이 PG 상태 조회로 확정한다.

작업 내용:
1. 설계 결정을 명시하고 고른다(트레이드오프 서술 필수):
   - PG 대상: 실 PG 샌드박스 연동 vs 자체 fake PG(실패·지연·비동기 승인 시뮬레이션 가능). 리포의 "클론만으로 기동" 지향을 유지하려면 fake PG(별도 모듈 앱 또는 compose 서비스)를 권장.
   - 흐름: 동기 승인 유지 + 웹훅·리컨실을 안전망으로 vs 전면 비동기(주문이 웹훅까지 PENDING 대기). 최소 변경은 전자.
2. PaymentGateway 포트에 거래 상태 조회를 추가하고 fake PG가 실패·지연 모드를 지원하게 한다.
3. PENDING 리컨실: 오래된 REQUESTED 결제를 주기적으로 PG 조회로 확정하는 스케줄러(제안: app-api 내 @Scheduled 최소 구성). 확정 결과에 따라 markPaid 또는 기존 보상 경로를 태운다. 재실행 멱등을 보장한다.
4. 웹훅을 도입하기로 결정했다면: 수신 엔드포인트 + 서명(또는 시크릿) 검증 + 중복 전달 멱등 처리. 인증 가드(2~4번)와의 관계(웹훅은 토큰 아님)를 명시한다.
5. 테스트: PG 실패 시 체크아웃 보상(기존 자산 재사용), 응답 유실→리컨실이 승인 확정→주문 PAID, 리컨실 반복 실행 멱등, (웹훅 시) 서명 불일치 거부·중복 전달 무해.
6. REQUIREMENTS.md·DOMAIN_MODEL.md의 "동기 stub 기준선"·"향후 확장" 서술을 새 능력·보장 수준으로 현행화한다.

하지 말 것: 다중 PG 라우팅, 결제 수단 확장, 부분 승인·부분 환불, 아웃박스(6번 소관).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, 문서가 새 결제 경계를 정확히 서술한다.
완료 후: 루트 todo.md의 5번 항목(결제 현실화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 6. 아웃박스(내구성 이벤트)

```text
[작업] 트랜잭셔널 아웃박스 도입 — 이벤트 발행 내구성 확보

배경(확인된 사실):
- 이벤트 전달은 InProcessMessagePublisher(infra-messaging, ApplicationEventPublisher 재발행)뿐이고 Javadoc이 "무손실·내구성 보장 없음(in-process 기준선)"을 명시한다.
- 발행은 OrderModifier.markPaid()의 OrderPaid 하나, 소비는 OrderPaidListener(AFTER_COMMIT + REQUIRES_NEW, 장바구니 비우기, 실패는 로그만 남기고 삼킴 — 비필수·멱등).
- REQUIREMENTS.md "향후 확장"에 아웃박스가 선언돼 있다.
- 착수 전 정직한 재평가가 조건이다: 소비자가 장바구니 비우기 하나뿐이면 유실 영향이 작아 아웃박스는 과설계일 수 있다. 5번(결제 현실화) 결과로 내구성이 필요한 이벤트 소비가 생겼는지 먼저 확인하고, 없다면 이 항목을 보류하고 그 판단을 todo.md에 기록하는 것도 유효한 완료다.

목표: 이벤트가 발행 트랜잭션과 원자적으로 저장되고, 프로세스 크래시 후에도 미발행분이 릴레이로 전달된다.

작업 내용:
1. 저장 위치 결정: 아웃박스 테이블을 어느 스키마에 둘지(발행 도메인 스키마 vs 전용 스키마) — docs/architecture.md의 리포지토리 접근 범위·모듈 의존 규칙과 정합하게 설계하고 근거를 남긴다. 신규 스키마면 SchemaFlywayFactory 갱신이 필요하다.
2. 발행 경로: MessagePublisher 포트는 유지하고 구현을 아웃박스 INSERT(발행 트랜잭션 내)로 교체한다.
3. 릴레이: 폴링 스케줄러가 미발행 행을 읽어 기존 in-process 전달로 재발행하고 완료 마킹한다. 재전달 허용(at-least-once) — 소비자 멱등을 전제로 명시한다. 처리 완료 행 정리 전략을 결정한다.
4. 테스트: 발행 트랜잭션 롤백 시 아웃박스 미저장, 커밋 후 릴레이 전달, 릴레이 중복 실행 무해, 소비 실패 후 재전달.

하지 말 것: 외부 브로커(Kafka 등)·CDC 도입 — in-process 소비를 유지한 채 내구성만 추가한다. 신규 이벤트 발굴(기존 발행 지점만).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, DOMAIN_MODEL.md의 "in-process 기준선" 서술이 새 보장 수준으로 현행화돼 있다. (보류 결정 시: 근거를 todo.md 항목에 남기는 것으로 갈음)
완료 후: 루트 todo.md의 6번 항목(아웃박스)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 7. 배송 후 환불(전체 주문 반품)

```text
[작업] DELIVERED 주문의 전체 반품→전액 환불 경로 추가

배경(확인된 사실):
- Order.cancel은 PAID에서 이행 PREPARING·ON_HOLD일 때만 허용되고 SHIPPED·DELIVERED는 CANCEL_NOT_ALLOWED로 거부한다 — 의도된 규칙이나, 대체 경로가 없어 DELIVERED 주문은 영구 환불 불가다.
- PaymentProcessor.cancel(APPROVED→CANCELLED 전액 환불, 멱등 키 "CANCEL:"+pgTransactionId)은 이미 구현돼 있다.
- 취소 조율은 OrderCancellationFacade가 담당한다(주문 CANCELLED 1회성 전이가 정확히-1회 보상의 가드).
- REQUIREMENTS.md·DOMAIN_MODEL.md가 부분 취소·부분 환불·반품(RMA)을 범위 밖으로 선언한다 — 이 작업은 그중 "전체 주문 반품"만 범위로 들이는 결정이다.

목표: DELIVERED 주문에 대해 관리자 액션으로 전체 주문 반품→전액 환불이 API로 완결되고, 이중 환불이 구조적으로 거부된다.

작업 내용:
1. 범위 선언 갱신: 전체 주문 반품 포함, 부분 반품·교환·반품 배송 추적은 계속 밖.
2. 상태 모델 설계(트레이드오프 명시): OrderStatus에 REFUNDED 신설 vs CANCELLED 재사용+사유 구분. DOMAIN_MODEL.md 상태 전이 요약과 정합하게 결정하고 근거를 남긴다. 절차는 최소안(요청→승인 2단계가 아닌 관리자 단일 액션)을 제안한다.
3. 부수 효과 결정(각각 근거 명시): 재고 복원 여부(반품 상품 재판매 가정), 사용 쿠폰 복원 여부(restoreUse는 있으나 만료 경과 가능). 기존 취소 보상과 규칙이 달라지는 지점을 명시한다.
4. 크로스 도메인 조율 파사드(order+payment±stock·coupon — OrderCancellationFacade 패턴) + 관리자 엔드포인트(제안: POST /api/v1/orders/{orderId}/refund, 사유 입력). 2~4번(인증) 완료 상태라면 관리자 가드를 적용한다.
5. 테스트: DELIVERED에서만 허용(PREPARING·SHIPPED·CANCELLED 거부), 환불 후 결제 CANCELLED·환불 거래 ID 존재, 이중 환불 거부(1회성 전이 가드), 부수 효과(재고·쿠폰) 결정대로 동작.

하지 말 것: 부분 반품·교환, 반품 요청/승인 워크플로, 배송 회수 추적.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, 두 문서의 상태 전이·범위 서술이 갱신돼 있다.
완료 후: 루트 todo.md의 7번 항목(배송 후 환불)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 8. 관측성

```text
[작업] Actuator health + 구조화 로깅 최소 구성

배경(확인된 사실):
- Actuator·Micrometer·tracing 의존성이 버전 카탈로그·전 모듈에 없고 management.* 설정도 없다. 유일한 헬스체크는 docker-compose의 pg_isready(컨테이너 수준)다.
- 로깅 설정 파일(logback-spring.xml 등)과 yml의 logging 키가 없다. 애플리케이션 로거 사용은 OrderPaidListener 한 곳뿐이다.
- Spring Boot 4.1 사용 — 구조화 로깅 내장 지원(logging.structured.format.*)을 우선 검토한다(외부 라이브러리 추가 전에 내장으로 충분한지 확인).

목표: app-api가 liveness/readiness health 엔드포인트를 노출하고, 콘솔 로그가 구조화(JSON) 포맷으로 나가며, 노출 범위가 의도적으로 최소다.

작업 내용:
1. spring-boot-starter-actuator 의존성 추가(버전 카탈로그 경유), management 설정: health(+liveness/readiness probe) 노출, 그 외 엔드포인트는 기본 비노출 유지. metrics 노출 여부는 판단하고 근거를 남긴다.
2. 구조화 로깅: 내장 structured logging 설정(포맷 선택 — ecs/logstash 중 근거와 함께). local 프로필에서는 사람이 읽는 포맷 유지 여부를 판단한다.
3. 요청 상관관계: 최소안으로 요청 단위 식별자(traceId성 MDC) 유무를 판단한다 — micrometer-tracing 도입은 과하면 보류하고 근거를 남긴다.
4. README에 확인 방법(health URL, 로그 포맷) 한 절 추가.
5. 테스트: health 엔드포인트 200 응답 통합 테스트 1개(기존 WebIntegrationTest 패턴).

하지 말 것: Prometheus 서버·Grafana 등 수집기 구성, 커스텀 메트릭 대량 추가, 분산 트레이싱 백엔드 연동.

완료 기준: 로컬 기동에서 health가 응답하고 로그가 구조화 포맷으로 출력되며, ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 8번 항목(관측성)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 9. 앱 컨테이너화

```text
[작업] app-migration·app-api 컨테이너 이미지 + compose 풀스택 기동

배경(확인된 사실):
- docker-compose.yml은 postgres 단일 서비스(postgres:17-alpine, 55432:5432, pg_isready 헬스체크)뿐이고 앱 Dockerfile이 없다.
- 실행 순서는 postgres → app-migration(원샷: SchemaFlywayFactory.migrateAll 후 종료) → app-api이며, 현재 README는 gradlew bootRun 경로만 안내한다.
- 프로필은 local 하나(55432 datasource)이고, 운용 datasource는 spring.datasource.* 환경변수 주입이 전제다(MigrationApplication Javadoc).
- Java 25 toolchain이다 — 이미지 빌드 방식의 Java 25 지원을 확인해야 한다.

목표: 클론 직후 docker compose 명령 하나로 postgres→마이그레이션→app-api가 순서대로 떠서 README 스모크(curl)가 성공한다.

작업 내용:
1. 이미지 빌드 방식 결정(트레이드오프 명시): Spring Boot bootBuildImage(빌드팩의 Java 25 지원 확인) vs 자체 Dockerfile(JRE 25 베이스). 두 앱에 같은 방식을 쓴다.
2. compose 확장: migration 서비스(원샷, depends_on postgres healthy, 종료 코드로 성공 판정), api 서비스(depends_on migration 완료), datasource는 환경변수 주입(컨테이너 내부에서는 호스트 55432가 아닌 서비스명:5432 — 프로필 추가 여부 판단). 기존 로컬 gradlew 실행 경로를 깨지 않는다.
3. README 갱신: 컨테이너 기동 경로를 추가하고 기존 gradlew 경로도 유지(두 경로의 용도 구분 한 줄).
4. 검증: 클린 상태(볼륨 삭제)에서 compose 기동 → README 스모크 절차 실제 실행 성공.

하지 말 것: k8s 매니페스트, 레지스트리 푸시·태깅 전략, 멀티 아키텍처 빌드.

완료 기준: 클린 컴포즈 기동에서 스모크가 성공하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 9번 항목(앱 컨테이너화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 10. 멱등 키 저장소 영속화

```text
[작업] 멱등 키 저장소를 인메모리에서 Redis 기반으로 교체

배경(확인된 사실):
- IdempotencyFilter(common-web)는 IdempotencyStore 포트를 쓰고 구현은 InMemoryIdempotencyStore뿐이다 — 재시작 시 소실, 다중 인스턴스에서 무력. REQUIREMENTS.md 제약·전제에 이 한계가 선언돼 있다. 포트 Javadoc이 분산 구현의 예로 Redis를 이미 지목한다.
- 필터 보장 수준: Idempotency-Key 헤더를 실은 unsafe 요청(POST·PUT·PATCH·DELETE)을 TTL 창 동안 잠그고 중복이면 409(응답 재생 없음). InMemory 시맨틱: tryBegin이 in-flight 락(5분)을 선점하고, complete가 dedup 창(10초)으로 단축한다.
- Redis 인프라가 전무하다: docker-compose에 redis 서비스 없음, 버전 카탈로그에 Redis 클라이언트 의존성 없음. 인프라 구현 모듈 선례는 module-infra:infra-messaging.

목표: 멱등 키가 Redis에 저장돼 재시작·다중 인스턴스에서도 중복 차단이 유지되고, 문서의 한계 선언이 새 보장 수준으로 갱신된다.

작업 내용:
1. 설계 결정 기록(트레이드오프 명시): Redis 채택 — Postgres 대안(신규 인프라 불요·내구성 기본 제공) 대비 근거를 남긴다: 이 보장 형상(TTL dedup 락, 응답 재생 없음)의 교과서 패턴이 SET NX PX이고, 네이티브 TTL로 만료 행 처리 전략 자체가 소거되며, 연습 프로젝트라 compose에 redis 추가 비용을 수용한다. Redis 장애 시 필터 동작(fail-open 통과 vs fail-closed 오류)을 결정하고 근거를 남긴다.
2. 인프라 배선: compose에 redis 서비스 추가(이미지 버전 고정·healthcheck). 재시작 소실 방지가 이 작업의 목표이므로 AOF(appendonly)를 켠다 — 기본 RDB 스냅숏만으로는 재시작 내구성이 안 된다. 클라이언트 의존성은 버전 카탈로그 경유(spring-boot-starter-data-redis 등 최소안 판단), 접속 설정은 local 프로필·compose full 프로필 환경변수 두 경로 모두 배선한다.
3. Redis 구현: SET NX PX로 원자 선점(동시 중복 요청 중 하나만 성공), complete는 TTL을 dedup 창으로 단축 — InMemory 시맨틱(in-flight 5분→완료 후 10초)을 보존한다. 구현 모듈 배치는 infra-messaging 선례를 따라 docs/architecture.md 모듈 규칙과 정합하게 정한다(common-web은 도메인·인프라 모듈이 아니다 — 배치를 아키텍처 규칙으로 검증).
4. 필터·포트는 그대로 두고 구현만 교체한다. InMemory 구현의 거취(테스트용 유지 여부)를 판단한다.
5. 테스트: Testcontainers Redis 기반 — 동시 중복 요청 1승 검증, TTL 경과 후 재사용 가능, 재시작 시나리오(Redis 컨테이너는 유지한 채 새 컨텍스트에서 같은 키 409 — 통합 테스트로 표현 가능한 수준까지).
6. REQUIREMENTS.md 제약·전제의 in-memory 한계 서술을 새 보장 수준(Redis 필요·AOF 전제 포함)으로 현행화한다. README 기동 안내에 redis를 반영한다.

하지 말 것: 응답 재생(response replay) 추가 — 현행 보장(중복 차단 409)을 유지한다. Redis 클러스터·센티널·Redlock, 캐시 등 멱등 외 용도 확장.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, 문서 제약 선언이 갱신돼 있다.
완료 후: 루트 todo.md의 10번 항목(멱등 키 저장소 영속화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 11. 주문 목록 페이지네이션

```text
[작업] 회원 주문 목록 조회에 페이지네이션 적용

배경(확인된 사실):
- GET /api/v1/orders는 List 전체를 반환한다. 경로: OrderReader.getOrdersByMember → OrderRepository.findByMemberIdOrderByCreatedAtDescIdDesc(@EntityGraph("lines"), 최신순+id 타이브레이커).
- 상품 목록에 기성 Page 패턴이 있다: ProductController.getProducts(page·size 파라미터, @Min 검증) → Page 반환 → ProductPageResponse(page/size/totalElements/totalPages).
- 발급쿠폰 목록은 회원당 발급이 정책당 1회라 바운드 — 이 작업에서 제외한다.
- 함정: 컬렉션 @EntityGraph(lines)와 Pageable을 한 쿼리에 섞으면 메모리 페이지네이션이 된다 — 설계 판단 필요.

목표: 주문 목록이 상품 목록과 같은 페이지 규약(page/size/totalElements/totalPages)으로 응답하고, 페치 전략이 메모리 페이지네이션을 피한다.

작업 내용:
1. 쿼리 설계(트레이드오프 명시): ID 페이지 조회 후 IN + @EntityGraph 재조회(두 쿼리) vs 다른 방식 — 메모리 페이지네이션 회피를 기준으로 결정하고 근거를 남긴다.
2. OrderReader에 페이지 메서드, 컨트롤러 파라미터·응답을 상품 목록 패턴(파라미터 검증 포함)으로 맞춘다. 기존 List 반환 형상은 제거한다(외부 클라이언트 부재 전제 — README 스모크에 영향 있으면 함께 갱신).
3. 3번 항목(인증 주체 강제) 완료 여부에 따라 memberId 소스가 다르다: 완료 전이면 기존 쿼리파라미터 유지, 완료 후면 토큰 주체 — 현재 상태를 확인하고 맞춘다.
4. 테스트: 페이지 경계(빈 페이지·마지막 페이지), 정렬 안정성(같은 시각 주문의 id 타이브레이커), 총계 정확성, size 검증.

하지 말 것: 상태·기간 필터, 발급쿠폰 목록 페이지네이션, 커서 페이지네이션(기성 오프셋 패턴 유지).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 11번 항목(주문 목록 페이지네이션)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 12. 출고 운송장 기록

```text
[작업] 출고(ship) 시 운송장 번호 기록 + 조회 노출

배경(확인된 사실):
- POST /api/v1/orders/{orderId}/ship은 인자가 없고 Order에 운송장 필드가 없다 — 이행 상태(SHIPPED·DELIVERED)만 있어 "출고했다"만 알 수 있다.
- REQUIREMENTS.md·DOMAIN_MODEL.md가 "배송 추적·택배 연동·운송장"을 범위 밖으로 선언한다(출고·배송완료 상태 자체는 포함) — 이 작업은 그중 "운송장 번호 기록"만 범위로 들이는 결정이다.
- ordering 스키마는 현재 V1 — 스키마별 Flyway라 V2 추가가 다른 도메인과 충돌하지 않는다.

목표: 출고 시 택배사·운송장 번호가 기록되고 주문 상세 응답에 노출된다. 택배사 API 연동·배송 추적은 계속 범위 밖.

작업 내용:
1. 범위 선언 갱신: 운송장 번호 기록 포함, 택배 연동·추적·반품 회수는 계속 밖.
2. 필드 설계 결정: carrier+trackingNumber vs 번호만 — 최소안을 근거와 함께 고른다. 필수 여부 결정(제안: ship 시 필수 — 없으면 기록 의미가 없다). Order 필드 + ordering 스키마 V2 마이그레이션.
3. OrderModifier.ship 시그니처 확장, ship 엔드포인트 요청 본문 추가(검증 포함), 주문 상세 응답 노출(미출고 주문은 null).
4. 테스트: ship 시 기록·상세 노출, 미출고 주문 null, 필수 검증 400, 기존 ship 전이 가드 회귀 유지.

하지 말 것: 택배사 enum 카탈로그 정교화, 배송 추적 상태·이벤트, 운송장 수정 API(요청 밖).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, 두 문서의 범위 서술이 갱신돼 있다.
완료 후: 루트 todo.md의 12번 항목(출고 운송장 기록)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 13. 쿠폰 발급 한도·발급분 무효화

```text
[작업] 쿠폰 정책 발급 한도(선착순) + 발급분 관리자 무효화 추가

배경(확인된 사실):
- Coupon 정책에 발급 한도가 없어 발급 가능 기간·회원당 1회((coupon_id, member_id) 유니크) 외에는 무제한 발급된다.
- IssuedCoupon 상태는 ISSUED/USED뿐(만료는 expiresAt 사용 시점 판정)이라 잘못 발급된 쿠폰을 회수할 수단이 없다 — 정책 DISABLE은 신규 발급만 차단하고 기발급분은 계속 사용 가능.
- IssuedCoupon에는 @Version 낙관락이 있다. REQUIREMENTS.md "향후 확장"이 "선착순 발급 한도"와 "발급 쿠폰 회수·관리자 무효화"를 선언한다.

목표: 정책에 총 발급 한도를 걸 수 있고(소진 시 발급 거부, 동시 발급 경합에서도 한도 초과 불가), 관리자가 특정 발급분을 무효화하면 그 쿠폰은 사용이 거부된다.

작업 내용:
1. 범위 선언 갱신: 두 항목을 "향후 확장"에서 범위로 옮긴다. 사용된(USED) 쿠폰의 소급 회수는 계속 밖으로 명시한다.
2. 발급 한도 — 동시성 설계가 핵심(트레이드오프 명시): 정책 행 발급 카운트+낙관락 vs 원자적 UPDATE(issued_count < limit) vs COUNT 쿼리 검사. 기존 재고 차감의 처리 방식(낙관락+409 클라이언트 재시도)과의 일관성을 검토해 결정하고 근거를 남긴다. 한도는 선택 필드(무제한 정책 유지 가능). coupon 스키마 마이그레이션 포함.
3. 무효화 — IssuedCoupon에 REVOKED 상태 신설: ISSUED→REVOKED만 허용(USED는 거부), REVOKED는 use에서 거부. 관리자 엔드포인트(제안: POST /api/v1/issued-coupons/{issuedCouponId}/revoke). 4번 항목(역할) 완료 상태면 관리자 가드 적용.
4. 테스트: 한도 소진 후 발급 거부, 동시 발급 경합에서 한도 초과 불가(초과 시도는 409 또는 발급 거부 — 설계 결정대로), REVOKED 사용 거부(체크아웃 통합 1케이스), USED 무효화 거부, 목록 조회에 상태 노출.

하지 말 것: 사용된 쿠폰 소급 회수·주문 금액 재계산, 재발급·양도, 무효화 사유 체계 정교화(사유 한 필드면 충분).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과하며, 두 문서의 상태·범위 서술이 갱신돼 있다.
완료 후: 루트 todo.md의 13번 항목(쿠폰 발급 한도·발급분 무효화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 14. API 문서화(OpenAPI)

```text
[작업] springdoc-openapi 자동 문서 + swagger-ui 노출

배경(확인된 사실):
- API 문서화 도구가 전무하다(springdoc·swagger·REST Docs 의존성 없음). 38개 엔드포인트는 코드 Javadoc과 루트 마크다운 문서로만 설명된다.
- Spring Boot 4.1 사용 — springdoc의 Boot 4 호환 버전을 확인해 버전 카탈로그에 추가해야 한다.
- 요청 DTO는 Bean Validation이 붙은 record, 오류 응답은 RFC 9457 problem+json(+커스텀 code) 규약이다.

목표: /v3/api-docs와 swagger-ui가 전 엔드포인트를 자동 생성으로 노출하고, 클라이언트가 문서만 보고 스모크 흐름(가입→상품→담기→체크아웃)을 재현할 수 있다.

작업 내용:
1. springdoc-openapi 의존성(버전 카탈로그 경유, Boot 4.1 호환 버전 확인) + 최소 설정(제목·버전 정도).
2. 자동 생성 우선 원칙: 컨트롤러에 @Operation 등 어노테이션을 대량 추가하지 않는다. 자동 생성 결과가 명백히 틀리는 지점(예: 204 응답, problem+json 오류 표현)만 최소 보정하고 보정 기준을 남긴다.
3. 노출 범위 판단: local 외 환경에서 swagger-ui 노출 여부(현재 프로필 체계 기준 최소안). 2~4번(인증) 완료 상태면 보안 스킴(bearer) 표기와 ui 접근 정책을 판단한다.
4. README에 문서 URL 한 줄 추가.
5. 검증: 기동 후 swagger-ui에서 대표 흐름 1회 수동 확인(문서 생성 오류·누락 엔드포인트 없는지).

하지 말 것: 전 엔드포인트 어노테이션 상세 기술, 별도 문서 사이트·버전드 스펙 파일 커밋, REST Docs 병행 도입.

완료 기준: 전 엔드포인트가 문서에 나타나고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 14번 항목(API 문서화)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 15. Clock 주입

```text
[작업] 시간 규칙 지점에 Clock 주입 — 시간 고정 테스트 가능화

배경(확인된 사실):
- java.time.Clock 빈·주입이 전무하고, Order·Payment·Member·Product·IssuedCoupon 엔티티와 CheckoutFacade가 Instant.now()를 직접 호출한다. 생성·수정 시각은 BaseTimeEntity(JPA auditing)가 자동 채운다.
- 시간이 규칙에 개입하는 지점: 쿠폰 발급 가능 기간 판정, 발급 시 expiresAt 스냅샷(발급시각+usageValidDays), 사용 시점 만료 판정. 그 외(paidAt·approvedAt 등)는 기록 시각이다.
- 현재 만료 경계(직전/직후) 테스트를 시간 고정으로 쓸 수 없는 구조다.

목표: 시간 규칙 지점이 주입된 시각으로 동작해 만료·기간 경계를 고정 시각 테스트로 검증할 수 있다.

작업 내용:
1. 접근 결정(트레이드오프 명시): 서비스 계층에 Clock 빈 주입 + 엔티티 전이·판정 메서드가 Instant 파라미터를 받는 방식(엔티티에 Clock 주입 금지)을 제안 — 다른 방식을 고르면 근거를 남긴다.
2. 적용 범위는 외과적으로: 시간 규칙 지점(쿠폰 발급 기간·만료)만 우선한다. 순수 기록 시각(paidAt 등)까지 확장할지는 판단하되, diff가 커지면 규칙 지점으로 한정하고 그 결정을 남긴다. JPA auditing은 그대로 둔다(연결은 선택).
3. Clock 빈(프로덕션 systemUTC) 구성 위치를 아키텍처 규칙(모듈 의존)과 정합하게 정한다.
4. 테스트: 만료 경계 직전 사용 성공·직후 거부, 발급 기간 경계, 기존 테스트 회귀 통과.

하지 말 것: Instant.now() 전면 일괄 치환으로 diff 폭발, 시간 관련 신규 기능(만료 상태 전이 배치 등 — 요청 밖).

완료 기준: 고정 시각 경계 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 15번 항목(Clock 주입)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

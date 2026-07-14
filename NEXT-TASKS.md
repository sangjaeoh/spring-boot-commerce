# 다음 작업 (우선순위별)

7개 도메인 구현이 끝난 시점의 남은 작업을 우선순위로 정리한다. 새 세션은 이 문서 → 규칙 문서(`docs/`) → 설계서(`DOMAIN-DESIGN.md`) → 자동 메모리(`commerce-implementation-progress.md`) 순으로 맥락을 잡는다.

## 현재 상태 (완료분)

- 공용: `common-core`(Money·UuidV7Generator·ErrorCode·BaseException), `common-jpa`(BaseTimeEntity·MoneyConverter·JpaAuditingConfig).
- 도메인 7개 전부: `member`·`product`·`stock`·`cart`·`coupon`·`order`·`payment`. 각각 entity·VO·enum·Info·repository·service·exception·Flyway + 단위 테스트 + Testcontainers 실 PostgreSQL 영속 슬라이스.
- `./gradlew build` 그린, 133 테스트 통과. `settings.gradle.kts`에 10개 모듈 등록.
- 각 도메인은 콜드 서브에이전트 리뷰를 거쳐 타당한 지적을 반영했다. 값 범위 거부 예외 정책은 Option C(설계 거부 목록에 있으면 도메인 예외 400, 호출자 보장 선행조건이면 `IllegalArgumentException`)로 확정해 `docs/coding-conventions.md`에 반영했다.
- 빌드 게이트 특성·복제 패턴·리뷰 로그의 상세는 메모리 `commerce-implementation-progress.md`에 있다. 남은 작업의 원본도 그 메모리에 기록돼 있다.

작업 방식은 지금까지와 동일: 규칙 문서를 지키고, 도메인/모듈 단위로 `./gradlew spotlessApply build`로 검증하며, 큰 단위 완료 후 콜드 서브에이전트 리뷰를 받고 타당한 것만 반영한다.

---

## P0 — 먼저 결정할 것 (app 계층을 막는 경계 정책)

### 1. 아키텍처 테스트의 엔티티 경계 규칙 정제

- 문제: `app-api/.../ArchitectureTest.java`의 규칙이 `..entity..` "패키지"에 의존하는 모든 클래스를 막는다. 그런데 각 도메인 `Info`는 `entity` 패키지의 상태 enum(MemberStatus·OrderStatus 등)을 노출하고, `place`/`variant create` 등의 명령 입력 타입(`OrderLineSnapshot`·`ProductOption`·coupon의 `Discount`/`ValidityPeriod`)도 `entity`에 있으며, `PaymentGateway` 포트는 `entity` enum(PaymentMethod·FailureReason)을 노출한다. app-api가 도메인을 의존하고 파사드·컨트롤러가 Info를 읽거나 명령을 구성하는 순간, 그리고 external-payment가 포트를 구현하는 순간 이 규칙이 깨진다. 지금은 app-api가 도메인을 의존하지 않아 규칙이 공허 통과 중이라 잠복해 있다.
- 결정 필요: 아래 중 택1(권장 (a)). 이 결정 없이는 아래 P1·P2가 곧바로 아키텍처 테스트에 막힌다.
  - (a) 규칙 대상을 `..entity..` 패키지 → `@Entity`/`@MappedSuperclass` 애노테이션 클래스로 바꾼다. 값 타입(enum·record·VO)은 경계 통과를 허용한다. 설계의 "엔티티 시그니처 등장 금지"(= JPA 엔티티) 의도에 부합하고, 세 이슈를 일괄 해소한다.
  - (b) 상태 enum·명령 입력 타입을 비-`entity` 패키지로 옮긴다.
  - (c) Info가 enum을 문자열로 평탄화한다.
- 함께: 소프트삭제 base finder(`findById` 등) 직접 호출 금지 ArchUnit 규칙을 이때 배선한다(member 리뷰 지적). `docs/architecture.md`의 "빌드가 강제하는 불변식"에 등재된 항목이다.
- 검증: app-api가 도메인을 의존하게 한 뒤 아키텍처 테스트가 실제로 위반을 잡는지 확인(현재는 도메인이 스캔되지 않아 공허 통과).

---

## P1 — 지원 인프라 (app 계층의 전제)

### 2. external-payment (`PaymentGateway` stub 어댑터)

- 목표: `domain-payment`의 `com.commerce.payment.port.PaymentGateway`를 구현하는 연습용 동기 stub. `convention.external-module` 적용, `externalModule { targetDomain.set("domain-payment") }` 설정.
- 전제: P0 (포트가 노출하는 `PaymentMethod`/`FailureReason`가 external에서 접근 가능해야 함).
- 설계: `DOMAIN-DESIGN.md` §7, §크로스 도메인(결제 5단계). approve는 성공/실패를 동기 반환, cancel은 취소 거래 ID 반환.

### 3. common-messaging (`MessagePublisher` 발행 포트)

- 목표: 벤더 중립 발행 포트. `convention.common-module` 적용. transport 구현은 infra(추후).
- 소비자: order의 `OrderPaid`(이미 정의됨) 커밋 후 발행.
- 설계: `docs/architecture.md`(common 배치·발행 포트), `DOMAIN-DESIGN.md` §도메인 이벤트 명세.

### 4. common-web (경계 공통)

- 목표: ProblemDetail 핸들러(`BaseException.getErrorCode()`의 `status()`→HTTP, 그 외 `IllegalArgumentException` 등은 500), 요청 DTO Bean Validation 승격처, 더블서밋 멱등 필터.
- 근거: Option C가 전제하는 "핸들러가 `BaseException`은 매핑, IAE는 500"을 실제 배선하는 곳. cart get-or-create 경합·체크아웃 더블서밋 방어(설계 범위 밖으로 미뤄둔 것)도 여기 멱등 필터가 담당.
- 설계: `docs/architecture.md`(common-web), `docs/coding-conventions.md`(ErrorCode 계약).

### 5. SchemaFlywayFactory (common-jpa) + `app-migration` 앱

- 목표: 7개 스키마(`member`·`product`·`stock`·`cart`·`coupon`·`ordering`·`payment`)의 Flyway를 스키마별로 실행하는 팩토리와 독립 실행 앱. 마이그레이션 SQL은 각 도메인 `src/main/resources/db/migration/{name}/`에 이미 있다.
- 근거: 전 도메인을 한 DB에 올려 `ddl-auto=validate`로 앱을 기동·검증하려면 필요. `docs/architecture.md`(스키마 등록·app-migration).

---

## P2 — app-api 계층 (크로스 도메인 정책)

설계의 핵심 흐름을 파사드가 조율한다. 파사드는 트랜잭션을 열지 않고 도메인 서비스를 조립하며, 각 서비스가 자기 트랜잭션을 소유한다(`docs/architecture.md` 트랜잭션 경계).

### 6. 파사드 (`facade`)

- 체크아웃(주문 생성→결제): `DOMAIN-DESIGN.md` §크로스 도메인(체크아웃 6단계). 검증(회원 자격·변형/상품/재고 주문가능·쿠폰) → 주문 PENDING 생성(사가 앵커) → 재고 차감 → 쿠폰 확정 → 결제(0원 PG 생략) → PAID + `OrderPaid` 발행. 실패 시 동기 보상.
- 취소·환불: §크로스 도메인(취소). 이중 가드(결제 취소 선행 → 주문 취소가 재고·쿠폰 복원 게이트).
- 상품 등록→첫 변형·재고 시딩: §크로스 도메인(상품 등록). HIDDEN → 변형 DISABLED → 재고 → enable → show.
- 회원 탈퇴 가드: §회원 탈퇴 가드. 미배송 PAID 주문 있으면 탈퇴 거부(파사드가 order 조회로 게이트).
- 전제: P1(external-payment·common-messaging), P0(파사드가 Info를 읽고 명령 입력을 구성).

### 7. 컨트롤러 + 요청/응답 DTO (`presentation/v1`)

- 목표: REST 엔드포인트, 요청 DTO(Bean Validation), 응답 DTO(Info→Response 변환은 앱이 소유). ID는 문자열로 전송.
- 전제: P1(common-web).

### 8. `OrderPaid` 리스너 (`event/listener`)

- 목표: `OrderPaid` 커밋 후 소비 → 장바구니 `removeItems`(멱등). `DOMAIN-DESIGN.md` §도메인 이벤트 명세.
- 전제: P1(common-messaging), 6(발행 배선).

### 9. 배선

- `JpaAuditingConfig` import, 이벤트 발행 배선, 컴포넌트 스캔 등. app 실행 앱 설정 불변식(`open-in-view: false`, `ddl-auto: validate`)은 이미 `application.yml`에 있음.

---

## P3 — 후속 (소비자 도입 시)

- 도메인 read 보강: `getCoupon`/`CouponInfo`(체크아웃이 minOrderAmount 필요), payment `getByOrderId`(취소 파사드), `OrderInfo`에 이행/취소 이력 필드(paidAt·cancelledAt·reason 등, 주문 상세 뷰 도입 시).
- 실 PG 이중청구 방어: 현재 PG 호출이 `@Transactional` 안이라 동기 stub 기준에선 무해하나 실 PG면 승인 후 롤백 시 재청구 위험. 멱등키·tx 밖 호출·리컨실 중 택. (payment 리뷰 m2)
- Testcontainers `org.testcontainers.containers.PostgreSQLContainer`(2.x에서 deprecated) → `org.testcontainers.postgresql.PostgreSQLContainer` 정리. 동작에는 문제없음.

---

## 명시적 범위 밖 (구현 안 함)

`DOMAIN-DESIGN.md` §명시적 범위 밖 참조: 배송 추적·택배 연동, 부분 취소/환불, 회원 주소록, 인증·로그인, 실 PG·웹훅·아웃박스, 쿠폰 선착순 한도, 발급 쿠폰 회수, 옵션 구조 정규화, 대표가 집계, admin/batch 앱 등.

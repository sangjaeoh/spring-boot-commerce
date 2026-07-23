# 작업 5-4: query 모듈 첫 도입 — 관리자 주문 검색

## 설계

### 현황

- `docs/architecture.md`가 query-{name} 계층(합성 불성립 크로스 도메인 조회 소유)을 정의했지만 실물 모듈이 0개, `convention.query-module` 플러그인도 미구현.
- 관리자 주문 목록은 `OrderReader.getOrdersByStatus(status, fulfillmentStatus, pageable)` — 상태 축만 지원. 회원 이메일 축이 없다.
- 이메일은 member 스키마(`member.member.email`), 주문은 ordering 스키마(`ordering.orders.member_id` 논리 참조). 필터 축이 두 도메인에 걸쳐 파사드 합성(주 도메인 페이지 + ID 장식)이 성립하지 않는다 — query 모듈 요건 충족.
- QueryDSL은 저장소에 미도입. 아키텍처 규칙은 QueryDSL 또는 `EntityManager`를 허용한다.
- `convention.app-module` 화이트리스트에 `:module-query:`가 빠져 있다(계층 표에는 허용).

### 결정

- 모듈명(선택지: query-order-search vs query-order): `query-order` — 베이스 패키지 규칙({그룹}.{접두}.{모듈명}, 하이픈→점)이 2요소 아티팩트명을 전제하므로 `com.commerce.query.order`로 기계 파생되는 이름을 택한다. 소비 표면은 관리자 주문 검색 하나다.
- 쿼리 구현(선택지: QueryDSL 도입 vs EntityManager JPQL): `EntityManager` JPQL. 첫 query 모듈에 QueryDSL 툴체인(apt·Q타입 생성) 도입은 과설계 — 조건 분기 2개뿐이다. `Order o, Member m` 크로스 조인 + `m.id = o.memberId` 동등 조건, 콘텐츠·카운트 쿼리 2개, `PageImpl` 조립.
- 검색 축(선택지: 이메일·상태 모두 필수 vs 이메일 필수·상태 옵셔널): 이메일 필수, 상태 옵셔널(`@Nullable OrderStatus`). 주 사용례가 "회원 이메일로 주문 찾기"라 상태 강제는 운영에 어긋난다. 옵셔널 분기는 JPQL 문자열 2갈래로 처리. 이메일은 정확 일치(유니크 인덱스 활용, 부분 일치는 요청 밖).
- provided·Info: `OrderSearchReader.getMemberOrderPage(String email, @Nullable OrderStatus status, Pageable pageable)` → `Page<OrderSearchInfo>`. `OrderSearchInfo`는 모듈 소유 record — orderId, orderNumber, memberId, memberEmail, status, fulfillmentStatus, payAmount(Money — 기존 OrderInfo 선례), orderedAt. 구현 `DefaultOrderSearchReader`는 package-private, `@Transactional(readOnly = true)` 안에서 Info 변환 완료.
- 모듈 구조: 도메인 구역 없이 평탄 — provided 인터페이스·Info·구현을 베이스 패키지에 둔다(architecture: "도메인 구역 구조는 두지 않는다", "기능은 provided로 제공하고 구현은 package-private").
- `convention.query-module` 플러그인: `convention.java-common` + `restrictProjectDependencies`로 `:module-domains:`·`:module-common:`만 허용. 이벤트 모듈 전환 등록 확장은 전환 실물이 나타날 때 추가한다(선제 설계 배제). spring-data-jpa 의존은 domain 플러그인과 동일하게 플러그인이 싣는다.
- `convention.app-module` 화이트리스트에 `:module-query:` 추가(계층 표와 정합화).
- 표면(선택지: GET /api/v1/admin/orders 확장 vs 별도 검색 경로): 별도 경로 `GET /api/v1/admin/orders/search`(email 필수·status 옵셔널·페이지네이션). 기존 목록의 필수 파라미터 계약을 건드리지 않아 완료 기준 3(기존 동작 유지)이 기존 테스트로 충족된다. 핸들러는 기존 `OrderAdminController`에 추가하고 `OrderSearchReader`를 주입한다(앱은 query 모듈 provided 주입 — 정상 경로). 응답은 admin 소유 `OrderSearchPageResponse`/`OrderSearchResponse` 신설.
- 아키텍처 테스트 확장:
  - JPA 엔티티 접근 규칙의 허용 대상에 query 모듈 추가(규칙 자체가 "소유 도메인과 query 모듈만 허용").
  - query 모듈 규칙 신설: 도메인 리포지토리 임포트 금지(도메인 상태 변경 금지의 강제 형태 — 쓰기는 리포지토리·EntityManager persist 경유인데 리포지토리 차단 + provided 시그니처 검사로 커버), provided·Info 시그니처 엔티티 비노출(기존 규칙의 적용 범위에 query 포함 확인), 의존 방향(도메인·infra·이벤트 모듈이 query를 의존하지 않음 — 컴파일 강제는 각 플러그인 화이트리스트가 이미 소유하므로 아키텍처 테스트는 임포트 수준 검증).
- 인덱스·마이그레이션: 불필요 — 이메일 정확 일치는 member.email 유니크 인덱스, 조인은 orders.member_id 기존 인덱스를 쓴다.

### 가정·트레이드오프

- 이메일 정확 일치만 지원한다. 부분 일치·정렬 축 추가는 요청 밖.
- fulfillmentStatus 축은 검색에 넣지 않는다 — WORKPLAN 축 정의(이메일+주문 상태)만 구현.
- `EntityManager` 동적 JPQL 2갈래는 상태 축이 늘면 QueryDSL 전환 후보다 — 현재는 최소.
- 탈퇴(소프트삭제) 회원의 이메일 검색: 활성 회원만 검색한다(`m.deletedAt is null`). 소프트삭제 조회 기본(삭제 미포함) 규칙 정합·부분 유니크 인덱스(`ux_member_email_active`) 사용 조건·탈퇴 후 재가입 이메일의 회원 혼합 방지 — 코드 리뷰에서 초안(미필터)의 세 가지 문제가 지적되어 전환했다.

### 완료 기준(검증 가능한 테스트 목록 — TDD)

query 슬라이스 `OrderSearchPersistenceTest`(module-query/query-order, @DataJpaTest + member·ordering 두 스키마 Flyway):

1. 이메일+상태 복합 조건 검색은 해당 회원의 해당 상태 주문만 최신순 페이지로 반환한다(타 회원·타 상태 제외).
2. 상태 없이 이메일만으로 검색하면 그 회원의 전 상태 주문이 반환된다.
3. 미존재 이메일 검색은 빈 페이지다.

웹 통합 `OrderAdminControllerTest`(app-admin):

4. 관리자 주문 검색은 200이고 이메일·상태 조건의 주문과 페이지 메타가 실린다.
5. 일반 회원의 검색은 403으로 거부된다.

아키텍처·회귀:

6. 아키텍처 테스트(모듈 경계·query 규칙) 통과 — 신설 규칙 포함.
7. 기존 상태 단독 필터 목록 테스트(`adminOrderListReturns...`) 통과 유지.

## 설계 리뷰

AGENTS.md 작업 원칙·docs 규칙 대조:

- (확인) 단순함 — QueryDSL 툴체인·이벤트 전환 등록·부분 일치·추가 정렬 축을 전부 배제. 신규 표면은 플러그인 1개·모듈 1개(파일 4~5개)·핸들러 1개·아키텍처 규칙 확장이다. 첫 실물로서의 최소 형태.
- (확인) query 모듈 요건 — 필터 축(이메일=member, 상태=order)이 두 도메인에 걸쳐 파사드 합성이 불성립. "합성이 성립하는 조회는 query 모듈에 두지 않는다" 위반 아님.
- (주의) 컨트롤러 의존 규칙 — `OrderAdminController`에 query provided를 추가 주입한다. 아키텍처 규칙 "최대 한 도메인의 service"가 query 모듈 provided를 도메인 서비스로 셀 경우 별도 컨트롤러 분리가 필요할 수 있다. 게이트에서 판정하고, 걸리면 규칙의 의도(도메인 조합은 파사드 소유)를 보존하는 방향으로 처리한다 — query 모듈은 조합이 아니라 단일 조회 제공자라 규칙 확장이 정당하다.
- (확인) 테스트 컨텍스트 — query 모듈 `@DataJpaTest`는 도메인 선례(`PersistenceTestConfig`: `@SpringBootConfiguration`+`@EnableAutoConfiguration`+Auditing)를 복제하고 member·ordering 두 스키마의 Flyway 로케이션을 함께 로딩한다. 엔티티 생성은 각 도메인의 public 팩토리(`Member.create`·`Order.place`)를 쓴다.
- (확인) 노출 규칙 — `OrderSearchInfo`의 Money는 기존 `OrderInfo` 선례(경계 record의 domain-shared 값 객체)와 동일. 엔티티·JPA 타입은 시그니처에 없다.

## 코드 리뷰

리뷰어 서브에이전트 파견(diff + docs 대조, 전 스위트·spotless 직접 실행, 아키텍처 규칙 변별력 검토). 판정: With fixes.

- (Important, 반영) 탈퇴 회원 미필터 — 초안 결정의 근거(유니크 인덱스 활용)가 사실 오류였다: `ux_member_email_active`는 `WHERE deleted_at IS NULL` 부분 인덱스라 무필터 쿼리에선 못 쓴다. 재가입 이메일 혼합 문제도 동반. `m.deletedAt is null` 필터 추가(TDD — 탈퇴 회원 빈 페이지 테스트 RED 확인 후 GREEN), Javadoc·설계 기록 갱신.
- (Important, 반영) 최신순 정렬 미검증 — 시나리오 2를 순서 단언(`containsExactly`)으로 전환, 밀리초 경계 대기 헬퍼로 결정성 확보(작업 5-1과 동일 수법).
- (Minor, 반영) EntityManager 쓰기 금지 규칙에 `executeUpdate`(벌크 JPQL) 축 추가, 미커버 우회(네이티브 SQL 문자열·Session unwrap)는 규칙 주석으로 리뷰 소유 명시. 영속 슬라이스 연결을 `@ServiceConnection`으로 전환(testing.md 문면 정합, static 마이그레이션 병행).
- (Minor, 기록) Info 변환이 `OrderSearchInfo.from(entity)` 대신 구현체 private `toInfo`에 있다 — 신규 아키텍처 규칙이 query 공개 타입의 모든 메서드 시그니처를 검사해 `from(Order, Member)` 정적 팩토리가 걸리는 구조적 긴장 때문. 도메인 info 규칙(필드만 검사, `from(entity)` 면제)과의 의도적 비대칭으로 기록한다.
- 리뷰어 확인 사항: 완료 기준 3건 실행 통과, 아키텍처 신규 규칙 4건의 변별력(failOnEmptyShould·패턴 정확성), 플러그인 화이트리스트 정확성, JPQL 파라미터 바인딩.

반영 후 query 슬라이스 4건·아키텍처 29건 통과 재확인.

# TODO 작업 요청 프롬프트

[`todo.md`](./todo.md)의 각 항목을 새 세션에서 작업 요청할 때 쓰는 프롬프트다. 항목 하나를 골라 코드 블록 안 내용을 그대로 붙여넣는다. 프롬프트는 자기완결이라 이 파일의 다른 부분 없이 단독으로 동작한다(레포 규칙은 CLAUDE.md→AGENTS.md가 자동 적용). 각 프롬프트는 마지막에 todo.md 체크 갱신 단계를 포함한다. 권장 실행 순서는 todo.md가 소유한다.

각 프롬프트의 엔드포인트 URL·요청 형상은 제안이다 — 기존 컨트롤러 관례(액션형 POST 서브리소스, `@RequestParam UUID memberId`, problem+json 오류 매핑)와 충돌하면 관례를 따른다.

## 1. 카탈로그 상품 목록 API

```text
[작업] 카탈로그 상품 목록 API 추가

배경(확인된 사실):
- ProductController에는 등록(POST /api/v1/products)과 단건 상세(GET /{productId})만 있다. 목록 조회가 없어 productId를 API 밖에서 알아야만 쇼핑이 시작된다.
- 단건 상세는 ProductDetailFacade가 상품·ACTIVE 변형·재고를 합성해 주문가능·품절·대표가를 파생한다. 목록은 이 파생 규칙을 그대로 재사용해야 한다.
- ProductReader에는 getProduct/getProducts(ids)만 있고 목록 메서드가 없다 — 이 작업은 유일하게 리포지토리 쿼리부터 새로 필요하다.
- 노출 규칙(DOMAIN_MODEL.md §2): 카탈로그 노출 = ON_SALE ∧ 미삭제 ∧ ACTIVE 변형 1개 이상. 품절 = ACTIVE 변형이 모두 소진(재고 quantity=0 또는 status≠SELLABLE)이어도 상품은 계속 노출. 대표가 = ACTIVE 변형 현재가에서 파생(저장 금지).
- product와 product_variant는 같은 product 스키마의 두 테이블이다(스키마 내 조인 가능 여부는 docs/architecture.md의 리포지토리 접근 범위 규칙을 먼저 확인). 재고는 stock 도메인 배치 조회(IN)로 합성한다.

목표: GET /api/v1/products (페이지네이션 파라미터 포함)가 노출 상품 목록을 대표가·품절 여부와 함께 반환하고, 숨김·삭제·ACTIVE 변형 0개 상품은 목록에서 빠진다.

작업 내용:
1. REQUIREMENTS.md·DOMAIN_MODEL.md §2·docs/architecture.md(리포지토리 접근 범위)를 읽고 설계를 잡는다. "ACTIVE 변형 ≥ 1" 필터를 페이지 산정에 어떻게 반영할지(쿼리 내 EXISTS vs 페이지 후 필터) 트레이드오프를 명시하고 하나를 골라 이유를 남긴다.
2. 도메인 계층: 목록 쿼리(리포지토리)와 Reader 메서드를 추가한다. 페이지네이션 필수.
3. 앱 계층: 목록 파사드(또는 ProductDetailFacade 확장)가 변형·재고를 배치 조회해 대표가·품절을 파생한다.
4. 컨트롤러: GET /api/v1/products 추가, 응답 DTO는 기존 response 패턴을 따른다.
5. 테스트: 기존 ProductDetailFacadeTest·ProductControllerTest 패턴으로 — 노출 규칙(숨김·삭제·ACTIVE 변형 0 제외), 품절 표시, 대표가, 페이지네이션 경계를 검증한다.

하지 말 것: 검색·정렬·카테고리·캐시 등 요청 밖 기능, 대표가 저장(파생만).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트(Spotless·NullAway·Error Prone·ArchUnit)가 통과한다.
완료 후: 루트 todo.md의 1번 항목(카탈로그 상품 목록 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 2. 주문 이행 전이 API

```text
[작업] 주문 이행(출고·배송완료·보류·해제) 전이 API 추가

배경(확인된 사실):
- OrderModifier에 ship/confirmDelivery/holdFulfillment/releaseFulfillment가 이미 구현·테스트돼 있으나 엔드포인트가 없다. 그래서 모든 PAID 주문이 영원히 PREPARING에 머문다.
- 연쇄 결함: "출고 이후 취소 거부" 규칙(DOMAIN_MODEL.md §6)이 영원히 발동 불가이고, 미배송 PAID 주문 탈퇴 거부 가드 때문에 결제한 회원은 취소 없이는 영구 탈퇴 불가다.
- 전이 규칙: 모든 이행 전진은 status=PAID에서만. PREPARING→SHIPPED→DELIVERED, PREPARING↔ON_HOLD(holdReason 세팅/클리어: FRAUD_REVIEW·ADDRESS_VERIFICATION·STOCK_DELAY).
- 단일 도메인(order) 쓰기라 파사드가 필요 없다(기존 관례: 크로스 도메인 조율만 파사드, 단건 조회는 컨트롤러가 Reader 직접 호출).

목표: PAID 주문을 API로 SHIPPED→DELIVERED까지 진행시킬 수 있고, 출고 후 취소가 HTTP로 거부되며, DELIVERED 후에는 해당 회원 탈퇴가 성공한다.

작업 내용:
1. OrderController에 엔드포인트 추가(제안): POST /api/v1/orders/{orderId}/ship, POST /{orderId}/delivery-confirmation, POST /{orderId}/fulfillment-hold(사유 입력), POST /{orderId}/fulfillment-release. 기존 POST /{orderId}/cancel 관례(액션형 POST, 204)를 따른다.
2. 컨트롤러는 OrderModifier에 얇게 위임한다. 오류는 기존 problem+json 매핑을 따른다(전이 위반·PAID 아님·미존재).
3. 테스트: 기존 OrderControllerTest·WebIntegrationTest 패턴으로 — 정상 전이 체인(PAID→ship→delivery-confirmation), PAID 아님(PENDING·CANCELLED) 거부, hold→release 왕복, ship 후 cancel 요청이 거부되는 HTTP 회귀, DELIVERED 후 회원 탈퇴 성공 시나리오.

하지 말 것: 운송장·배송 추적·택배 연동(범위 밖), 새 상태·필드 추가(도메인은 완성돼 있다).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 2번 항목(주문 이행 전이 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 3. 재고 운영 API

```text
[작업] 재고 운영(재입고·수동 품절/재개·단종) API 추가

배경(확인된 사실):
- StockModifier에 increase(재입고)/markSoldOut/markSellable/discontinue가 이미 구현·테스트돼 있으나 컨트롤러가 없다(StockController 부재). 그래서 재고가 소진되면 API로는 영구 품절이다.
- 규칙(DOMAIN_MODEL.md §3): increase는 취소 보상 restore와 별개 의도(신규 물량 입고)이며 DISCONTINUED엔 거부. 전이는 SELLABLE↔SOLD_OUT, {SELLABLE,SOLD_OUT}→DISCONTINUED(종료). 재고는 변형당 1행(variantId 유니크).
- 단일 도메인 쓰기라 파사드가 필요 없다.

목표: 소진된 변형을 API 재입고로 다시 주문 가능하게 만들 수 있고, 수동 품절/재개·단종을 API로 전환할 수 있다.

작업 내용:
1. StockController 신설(제안): POST /api/v1/stocks/{variantId}/increase(수량 입력, ≥1 검증), POST /{variantId}/mark-sold-out, POST /{variantId}/mark-sellable, POST /{variantId}/discontinue. variantId 키 사용은 재고가 변형당 1행이라 자연 키다.
2. StockModifier에 얇게 위임, 오류는 problem+json 매핑(미존재·잘못된 전이·단종 재입고 거부).
3. 테스트: 기존 컨트롤러 테스트 패턴으로 — 재입고 후 수량 반영, DISCONTINUED 재입고 거부, 전이 성공/거부. 통합 시나리오 하나: 체크아웃으로 소진 → increase → 재체크아웃 성공.

하지 말 것: 재고 단건 조회 API(상품 상세 파사드가 이미 파생 노출), 예약(홀드) 모델 도입.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 3번 항목(재고 운영 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 4. 추가 변형 등록 + 변형 관리 API

```text
[작업] 기존 상품에 변형 추가 등록 + 변형 관리(가격 변경·활성/비활성/은퇴) API 추가

배경(확인된 사실):
- ProductRegistrationFacade.registerProduct는 상품 등록 시 첫 변형 1개만 시딩한다. 추가 변형 등록 경로(파사드·엔드포인트)가 없어 옵션 상품(색상·사이즈 등 변형 2개 이상)을 만들 수 없다.
- DOMAIN_MODEL.md "상품 등록 → 첫 변형·재고 시딩" 절에 추가 변형 흐름이 이미 확정돼 있다: 변형 create(DISABLED) → 재고 create(초기수량) → enable. 파괴적 보상 없음(실패 시 DISABLED 변형·재고가 남고 재시도는 남은 단계 재개 — 같은 옵션 재-create는 비-RETIRED 유니크에 막히므로 재생성이 아니라 재개).
- ProductVariantModifier에 changePrice/enable/disable/retire가 이미 구현돼 있으나 미노출이다. 규칙: 가격 ≥ 1, RETIRED는 모든 변경 거부, (product_id, option_signature) 비-RETIRED 유니크(중복 조합 거부), 은퇴 조합은 새 변형으로 재등록 가능.
- 변형 추가는 크로스 도메인(product+stock)이라 파사드 소관, 변형 관리(가격·상태)는 단일 도메인이라 컨트롤러→Modifier 직행이 관례에 맞다.

목표: 기존 상품에 두 번째 이상 변형을 API로 등록해 주문까지 가능하고, 변형 가격 변경·활성/비활성/은퇴가 API로 동작한다.

작업 내용:
1. ProductRegistrationFacade에 addVariant(productId, price, options, initialQuantity) 추가 — 문서의 시딩 순서(create DISABLED→재고 create→enable)를 따른다. registerProduct의 기존 시딩 로직과 중복되면 공유를 검토하되 과추상화하지 않는다.
2. 엔드포인트(제안): POST /api/v1/products/{productId}/variants (추가 등록), POST /api/v1/product-variants/{variantId}/price(또는 PATCH — 기존 관례 우선), POST /{variantId}/enable, /{variantId}/disable, /{variantId}/retire.
3. 테스트: 추가 변형 등록 후 상품 상세에 두 변형 노출·체크아웃 가능, 중복 옵션 조합(비-RETIRED) 409, 은퇴 후 같은 조합 재등록 성공, RETIRED에 가격 변경·enable 거부, 가격 변경이 기존 주문 스냅샷에 무영향.

하지 말 것: 옵션 구조 정규화(옵션 엔티티·옵션 카탈로그 — 범위 밖), 첫 등록 API의 형상 변경.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 4번 항목(추가 변형 등록 + 변형 관리 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 5. 상품 관리 API

```text
[작업] 상품 관리(노출·숨김, 이름·설명 편집, 논리삭제) API 추가

배경(확인된 사실):
- ProductModifier(show/hide/rename/changeDescription)와 ProductRemover(논리삭제)가 이미 구현돼 있으나 엔드포인트가 없다. 컨트롤러에는 등록과 단건 상세뿐이다.
- 규칙(DOMAIN_MODEL.md §2): ON_SALE↔HIDDEN 전이 가드, 이름·설명 편집은 기존 주문 스냅샷에 무영향, 삭제는 deletedAt 논리삭제이며 변형을 연쇄 삭제하지 않는다. 숨김·삭제 상품은 담기·체크아웃 게이트가 거른다(이미 구현).
- 단일 도메인 쓰기라 파사드가 필요 없다.

목표: 상품을 API로 숨기고 다시 노출하고, 이름·설명을 고치고, 논리삭제할 수 있다.

작업 내용:
1. ProductController에 추가(제안): POST /api/v1/products/{productId}/show, POST /{productId}/hide, PATCH /{productId}(name·description — 별도 요청 DTO), DELETE /{productId}.
2. Modifier/Remover에 얇게 위임, 오류는 problem+json 매핑(미존재·잘못된 전이).
3. 테스트: show/hide 전이 성공·거부, hide된 상품이 담기·체크아웃에서 거부되는 통합 확인(기존 게이트 재검증 1케이스면 충분), rename 후 기존 주문 productName 스냅샷 불변, delete 후 상세 조회 미존재 처리.

하지 말 것: 카탈로그 목록 관련 작업(별도 항목), 변형 연쇄 처리 추가.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 5번 항목(상품 관리 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 6. 회원 관리 API

```text
[작업] 회원 관리(정지·해제, 이름 변경) API 추가

배경(확인된 사실):
- MemberModifier에 suspend(reason)/reinstate/rename이 이미 구현돼 있으나 엔드포인트가 없다. MemberController에는 가입(POST)·단건 조회(GET /{memberId})·탈퇴(DELETE /{memberId}?reason=)만 있다.
- 규칙(DOMAIN_MODEL.md §1): ACTIVE↔SUSPENDED 전이, suspend는 suspensionReason(FRAUD_SUSPECTED·PAYMENT_ABUSE·POLICY_VIOLATION·CS_MANUAL) 세팅·reinstate는 clear. 정지와 탈퇴는 독립 축. 정지 회원은 담기·체크아웃·쿠폰 발급이 거부된다(게이트 이미 구현). 이메일은 불변, rename은 이름만.
- 탈퇴 엔드포인트가 @RequestParam으로 사유를 받는 기존 관례가 있다.

목표: 회원을 API로 정지·해제하고 이름을 변경할 수 있으며, 정지 회원의 담기·체크아웃이 실제로 거부된다.

작업 내용:
1. MemberController에 추가(제안): POST /api/v1/members/{memberId}/suspend?reason=, POST /{memberId}/reinstate, PATCH /{memberId}(새 이름 — 요청 DTO). 사유 전달은 탈퇴(DELETE ?reason=) 관례를 따른다.
2. MemberModifier에 얇게 위임, 오류는 problem+json 매핑(미존재·잘못된 전이).
3. 테스트: 정지→해제 왕복(사유 세팅·클리어 확인), 정지 중 담기 또는 체크아웃 거부 통합 1케이스, 정지된 채 탈퇴 가능(독립 축), rename 후 이메일 불변.

하지 말 것: 관리자 인증·권한 분리(범위 밖 — 기존 단일 표면 유지), 회원 목록 API(요청 밖).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 6번 항목(회원 관리 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 7. 쿠폰 정책 전환 + 내 발급 쿠폰 목록 API

```text
[작업] 쿠폰 정책 발급 가능·중지 전환 API + 회원별 발급 쿠폰 목록 API 추가

배경(확인된 사실):
- CouponModifier.disable/enable이 이미 구현돼 있으나 엔드포인트가 없다(CouponController에는 정책 생성·발급만 있다).
- 발급 쿠폰 조회는 GET /api/v1/issued-coupons/{issuedCouponId}?memberId= 단건뿐이다. 회원별 목록이 없어 클라이언트가 발급 응답의 ID를 기억하지 않으면 체크아웃에서 쿠폰을 쓸 수 없다. IssuedCouponReader에 목록 메서드부터 없다(getIssuedCoupon·calculateDiscount뿐).
- 규칙(DOMAIN_MODEL.md §5): 정책 ACTIVE↔DISABLED, DISABLED는 신규 발급만 막고 기발급분은 계속 사용 가능. 발급분 접근은 본인 소유만(미소유는 미존재 취급). 만료는 expiresAt 사용 시점 판정(별도 상태 없음).

목표: 정책을 API로 중지·재개할 수 있고(중지 후 신규 발급 거부·기발급분 사용 가능), 회원이 자기 발급 쿠폰 목록을 조회할 수 있다.

작업 내용:
1. CouponController에 추가(제안): POST /api/v1/coupons/{couponId}/disable, POST /{couponId}/enable.
2. IssuedCouponRepository에 회원별 조회, IssuedCouponReader에 목록 메서드 추가(최신순 — OrderReader.getOrdersByMember 관례). 상태·만료 필터는 넣지 않고 전량 반환한다(표시 판단은 클라이언트 몫, 요청 밖 기능 금지).
3. IssuedCouponController에 GET /api/v1/issued-coupons?memberId= 추가, 기존 IssuedCouponResponse 재사용.
4. 테스트: disable 후 발급 409·기발급분 사용(체크아웃) 성공, enable 후 발급 재개, 목록이 본인 것만·최신순 반환.

하지 말 것: 발급분 회수·무효화(범위 밖), 페이지네이션(회원당 발급은 정책당 1회라 소량).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 7번 항목(쿠폰 정책 전환 + 내 발급 쿠폰 목록 API)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 8. 결제 거래 추적 노출

```text
[작업] 주문 결제 정보(승인·환불 거래) 조회 API 추가

배경(확인된 사실):
- REQUIREMENTS.md 결제 절은 "승인 거래와 취소(환불) 거래를 각각 식별·추적할 수 있다"를 요구하지만, Payment는 어떤 응답에도 노출되지 않는다(OrderResponse에 결제 정보 없음, PaymentController 없음).
- Payment 엔티티는 pgTransactionId(승인)·pgCancelTransactionId(환불)·method·failureReason·approvedAt·cancelledAt을 이미 보유하고, PaymentReader.getByOrderId(orderId)도 이미 있다 — 얇은 노출만 필요하다.
- 0원 결제(전액 할인)는 PG 생략 자동 승인이라 pgTransactionId·method가 null일 수 있다(불변식 amount>0 ⇔ method≠null).

목표: 주문 ID로 결제의 상태·수단·금액·승인/환불 거래 ID·실패 사유·시각을 조회할 수 있다.

작업 내용:
1. 엔드포인트(제안): GET /api/v1/orders/{orderId}/payment — OrderController에 두고 PaymentReader.getByOrderId에 위임(앱 계층은 모든 도메인을 조립 가능). OrderResponse 확장 대신 별도 엔드포인트를 제안하는 이유: 주문 조회마다 결제 fan-out을 강제하지 않고, 도메인별 응답 관례를 유지한다. 기존 관례와 판단이 다르면 근거를 남기고 조정한다.
2. PaymentResponse DTO 신설: status, method(널 가능), amount, failureReason(널 가능), pgTransactionId(널 가능), pgCancelTransactionId(널 가능), approvedAt, cancelledAt.
3. 테스트: 체크아웃 성공 주문의 결제 조회(APPROVED·거래 ID 존재), 취소 후 조회(CANCELLED·환불 거래 ID 존재·승인 ID와 별개), 0원 결제(거래 ID·method null), 결제 없는 주문 ID 미존재 처리.

하지 말 것: 결제 수정·재시도 API(범위 밖), Payment 필드 추가.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 8번 항목(결제 거래 추적 노출)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 9. 로컬 실행 구성 + README

```text
[작업] 로컬 실행 구성(docker-compose + 프로필)과 README 추가

배경(확인된 사실):
- app-api의 application.yml에는 datasource가 없고(spring.application.name·jpa.ddl-auto=validate·open-in-view=false뿐), docker-compose·README도 없어 리포만으로 앱을 띄울 수 없다. 테스트는 Testcontainers(SharedPostgresContainer)로만 돈다.
- 스키마 생성은 별도 앱 app-migration이 담당한다 — SchemaFlywayFactory(common-jpa)가 7개 도메인 스키마별 Flyway를 프로그램적으로 실행하고, Boot의 Flyway 오토컨피그는 의도적으로 꺼져 있다(app-migration application.yml 주석 참조: 켜지면 7개 V1__이 버전 충돌). 이 구조를 바꾸지 않는다.
- Flyway SQL은 각 도메인 모듈의 db/migration/{schema}/에 있다.

목표: 클론 직후 README의 명령 몇 개로 Postgres 기동 → 마이그레이션 → app-api 기동 → 회원가입·상품등록·담기·체크아웃 curl 스모크가 성공한다.

작업 내용:
1. docker-compose.yml(루트): postgres 단일 서비스, DB·계정은 로컬 프로필과 일치시킨다.
2. app-api·app-migration에 local 프로필 datasource 설정을 추가한다(기존 application.yml 형상 유지, 프로필 분리). Testcontainers 기반 테스트 설정에 영향이 없어야 한다.
3. README.md(루트): 프로젝트 한 줄 소개, 모듈 구조 개요(module-apps/common/domains/external/infra), 문서 지도(REQUIREMENTS.md·DOMAIN_MODEL.md·docs/), 실행 순서(compose up → app-migration 실행 → app-api 실행), 체크아웃까지의 curl 스모크 예시(멱등 키 헤더 포함), 테스트 실행법(./gradlew build).
4. 검증: 문서의 명령을 실제로 그대로 실행해 스모크가 성공하는지 확인한다(문서만 쓰고 끝내지 않는다).

하지 말 것: CI·배포 구성(요청 밖), 마이그레이션 실행 방식 변경, docs/ 규칙 문서 수정.

완료 기준: README 절차 그대로의 신선한 실행(클린 DB)이 스모크까지 성공하고 ./gradlew build가 통과한다.
완료 후: 루트 todo.md의 9번 항목(로컬 실행 구성 + README)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

## 10. 문서 현행화(멱등 필터)

```text
[작업] 멱등 필터 구현 완료를 REQUIREMENTS·DOMAIN_MODEL에 현행화

배경(확인된 사실):
- IdempotencyFilter는 이미 구현·배선·검증돼 있다: common-web의 OncePerRequestFilter(Idempotency-Key 헤더를 실은 unsafe 요청을 InMemoryIdempotencyStore로 잠그고 중복이면 409 problem+json), app-api에 runtimeOnly 배선, 체크아웃·취소 HTTP 멱등 회귀 테스트 존재(같은 키 재요청 409).
- 그런데 문서는 이를 아직 미래 항목으로 둔다: REQUIREMENTS.md "향후 확장"의 "체크아웃 동시 더블서밋 방어(멱등 필터)" 항목, DOMAIN_MODEL.md "명시적 범위 밖" 마지막의 "체크아웃 동시 더블서밋 방어(멱등 필터는 추후 common-web 도입 시)" 항목.
- DOMAIN_MODEL.md 취소·환불 절의 잔여 서술("성공 완결 후 요청 재전송으로 인한 중복 취소 호출은 재고를 이중 복원한다. …재호출의 멱등은 HTTP 경계 책임으로 범위 밖")도 점검 대상이다 — HTTP 경계 방어가 이제 존재하므로, 필터의 보장 수준(같은 Idempotency-Key·TTL 창 내 재요청만 차단하는 best-effort이며 in-memory 단일 인스턴스 한정, 응답 재생 없음)을 정확히 반영해 서술을 조정한다. 과장 금지: 필터가 라인별 exactly-once를 만들지는 않는다.

목표: 두 문서가 멱등 필터를 미래가 아닌 현재 능력으로 정확한 보장 수준과 함께 서술하고, 문서끼리 모순이 없다.

작업 내용:
1. REQUIREMENTS.md: "향후 확장"에서 해당 항목을 제거하고, 현재 능력으로서 적절한 절(비기능 요구사항의 정합성 부근 또는 제약·전제)에 현재형 한 줄로 옮긴다. in-memory 단일 인스턴스 한계는 제약·전제에 남긴다.
2. DOMAIN_MODEL.md: "명시적 범위 밖"에서 해당 항목을 제거하고, 취소·환불 절의 잔여 서술을 필터 존재·보장 수준에 맞게 조정한다.
3. 편집 규약(AGENTS.md) 준수: 현재 상태로 서술, 편집 이력 서술 금지, 한 규칙은 한 문서 소유(필터 메커니즘 상세는 코드/규칙 문서 몫 — 여기선 능력·보장 수준만).
4. 그 외 문서-구현 불일치를 발견하면 고치지 말고 보고만 한다(외과적 수정).

하지 말 것: 코드 변경, 멱등 저장소 개선(DB 저장소 등 — 별도 결정).

완료 기준: 두 문서 diff가 위 범위에 한정되고, 서술이 실제 필터 동작(헤더 opt-in·unsafe 메서드·409·TTL 창·in-memory)과 일치한다.
완료 후: 루트 todo.md의 10번 항목(문서 현행화(멱등 필터))을 체크([ ] → [x])로 갱신한다. 커밋 및 메인머지, 잔여브랜치 삭제
```

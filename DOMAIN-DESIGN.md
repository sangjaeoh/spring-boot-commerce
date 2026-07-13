# 커머스 도메인 설계

이 문서는 커머스 최소 구현의 도메인 모델을 소유한다 — 어떤 도메인이 있고, 각 도메인이 어떤 필드·상태·정책·불변식을 갖는지. 모듈 구조·빌드 순서 같은 "어떻게 세우는가"는 이 문서의 범위가 아니다(그것은 아키텍처 규칙 문서 `docs/`와 구현 계획이 소유한다).

설계 규칙(ID·연관·상태 전이·소프트삭제 등)은 `docs/architecture.md`·`docs/entity-persistence.md`·`docs/coding-conventions.md`를 따른다. 이 문서는 그 규칙을 이 앱의 7개 도메인에 적용한 결과다.

범위는 7개 도메인: 회원(member) · 상품(product) · 재고(stock) · 장바구니(cart) · 쿠폰(coupon) · 주문(order) · 결제(payment).

기준선(이 문서의 전제): 결제 게이트웨이는 동기 stub, 도메인 이벤트 transport는 in-process(무손실 보장 아님), 로그인·인증은 범위 밖.

## 공통 규약

- 식별자: 모든 엔티티 PK는 앱에서 생성하는 UUIDv7. `@GeneratedValue` 없음.
- 크로스 도메인·애그리거트 참조: 순수 `UUID xxxId` 값만 보관한다. 물리 FK·객체 연관 없음(같은 애그리거트 내부만 객체 연관).
- 시각: `createdAt`·`updatedAt`은 JPA Auditing이 채운다(엔티티가 직접 선언하지 않음).
- 삭제: 논리삭제 기본. 삭제 지원 엔티티는 nullable `deletedAt`을 두고 활성 조회에서 제외한다.
- 금액: `Money`(원 단위 정수, 0 이상). `Money`는 프레임워크 의존 없는 범용 값 타입이라 `common-core`가 소유하고(도메인 지식 아님), JPA 변환기(`MoneyConverter`)는 `common-jpa`에 단 하나 둔다. 통화는 KRW 단일로 가정한다.
- 수량: 정수. 별도 명시가 없으면 1 이상.
- 낙관락: 기본으로 두지 않는다. 실 경합이 있는 재고 차감·쿠폰 사용에만 `@Version`을 둔다.
- 상태: enum(문자열 저장). 상태 변경은 의도 동사 메서드 + 허용 전이 가드로만 한다(setter 없음). 상태는 실세계 전이·정책으로 정당화되면 선제적으로 둔다(소비자가 아직 없어도 근거 있는 현실 상태는 모델에 둔다). 근거 없이 아무도 전이시키지 않는 "죽은 상태"만 피한다.
- 표기: 아래 각 도메인 필드 표는 이 공통 필드도 함께 명시한다 — 모든 엔티티는 `id`·`createdAt`·`updatedAt`을 가지며, 해당 도메인에 한해 `deletedAt`·`version`을 갖는다.

## 공용 값 객체 (Money)

`Money`는 여러 도메인(product·coupon·order·payment)이 공유하는 값 객체라 `common-core`가 소유한다. 도메인 전용 값 객체(`Email`·`Address`)는 각 도메인 절에 명시한다.

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| amount | long | 필수 | 원 단위 금액. 0 이상. 단일 컬럼 매핑(`AttributeConverter`) |

- 불변 값이고 `plus`·`minus`·`multiply`로 새 값을 만든다(뺄셈 결과가 음수면 예외). 통화는 KRW 단일.

## 1. 회원 (member)

회원의 식별을 소유한다. 인증(로그인·토큰)은 범위 밖이라 자격증명(비밀번호)을 두지 않는다.

- 애그리거트 루트: `Member`
- 스키마/테이블: `member` / `member`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| email | Email(VO) | 필수 | 활성 회원 중 유니크. 형식 검증(VO). 식별 키 |
| name | String | 필수 | 표시 이름 |
| status | MemberStatus | 필수 | 아래 상태표 |
| suspensionReason | SuspensionReason | 선택 | 정지 사유. SUSPENDED에서만 존재, `reinstate` 시 clear |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 논리삭제(탈퇴) 시각 |
| withdrawalReason | WithdrawalReason | 선택 | 탈퇴 사유(`delete` 시 세팅). 탈퇴분에서만 존재 |

### 상태 (MemberStatus)

| 상태 | 의미 |
|---|---|
| ACTIVE | 정상. 담기·주문 자격 있음 |
| SUSPENDED | 관리자 정지. 존재하나 신규 담기·주문 불가(되돌릴 수 있음) |

- 전이: `ACTIVE ↔ SUSPENDED`. 최초 상태 ACTIVE. 탈퇴는 status 값이 아니라 `deletedAt`으로 표현한다 — 정지와 탈퇴는 독립 축이라 정지된 채 탈퇴, 탈퇴 없이 정지가 모두 가능하다(한 컬럼에 겹치지 않는다).
- 정지 사유: `SUSPENDED`는 `suspensionReason`(`FRAUD_SUSPECTED`·`PAYMENT_ABUSE`·`POLICY_VIOLATION`·`CS_MANUAL`)을 함께 기록한다. 상태값이 아닌 부가 컨텍스트라 status 축과 겹치지 않는다.

### 값 객체 (Email)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| value | String | 필수 | 이메일 주소. 형식 검증(정규식). 단일 컬럼 매핑(`AttributeConverter`) |

### 정책·불변식

- 가입(register): 필수값은 email, name. 가입 즉시 주문·장바구니를 쓸 수 있다(별도 활성화 절차 없음).
- 이메일 유니크: 활성 회원 사이에서 유니크. 탈퇴한 이메일은 재가입 가능 — DB에서 부분 유니크 인덱스(`WHERE deleted_at IS NULL`)로 강제한다.
- 탈퇴(delete): `deletedAt`·`withdrawalReason`(`NO_LONGER_USED`·`PRIVACY_CONCERN`·`DISSATISFIED`·`SWITCHED_SERVICE`) 세팅(논리삭제). 탈퇴 회원은 기본 조회에서 제외.
- 주문 자격: 담기·주문은 자격 활성 회원만 — `status = ACTIVE` 이고 미탈퇴(`deletedAt IS NULL`). 체크아웃·담기에서 검증.
- 활성 두 축 분리: 영속 활성(`deletedAt IS NULL`, SUSPENDED 포함 — 운영이 정지 회원을 조회·`reinstate`) vs 자격 활성(`status = ACTIVE`, 담기·체크아웃 자격). 조회는 정지 회원을 포함하고 자격 검증만 정지를 차단한다.
- 탈퇴·정지 비연쇄: 탈퇴·정지해도 회원의 장바구니·발급 쿠폰·PENDING 주문은 그대로 남고, 다음 체크아웃·담기의 자격 검증에서만 차단한다(연쇄 정리 없음).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | email, name | 활성 회원 사이 이메일 유니크, 이메일 형식 | 이메일 중복; 이메일 형식 오류 |
| suspend | memberId, reason | ACTIVE→SUSPENDED, `suspensionReason` 세팅 | 미존재; 잘못된 전이 |
| reinstate | memberId | SUSPENDED→ACTIVE, `suspensionReason` clear | 미존재; 잘못된 전이 |
| rename | memberId, newName | `name` 갱신(`email` 불변) | 미존재 |
| delete | memberId, reason | `deletedAt`·`withdrawalReason` 세팅(논리삭제) | 미존재 |
| getMember | memberId | 활성 회원 1행(SUSPENDED·suspensionReason 포함) | 미존재 |

반환 형상(명령/조회)·거부의 예외 매핑은 `docs/coding-conventions.md`가 소유. 서비스 역할 배치·네이밍도 같은 문서가 소유한다.

## 2. 상품 (product)

판매 상품의 카탈로그 그룹(`Product`)과 그 판매·재고 단위인 상품변형(`ProductVariant`)을 소유한다. 카탈로그 정보는 `Product`가, 판매가·옵션은 변형이 소유하고, 재고 수량은 별도 재고 도메인이 소유한다. 그룹과 변형은 생명주기가 달라(변형은 시간차 추가·개별 비활성/은퇴·개별 가격) 서로 다른 애그리거트이고 `UUID productId`로 잇는다(coupon 정책·발급분과 같은 두 루트 구조).

- 애그리거트 루트: `Product`(카탈로그 그룹), `ProductVariant`(판매·재고 단위)
- 스키마/테이블: `product` / `product`, `product_variant`

### Product 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 상품명 |
| description | String | 선택 | 상세 설명 |
| status | ProductStatus | 필수 | 아래 상태표 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 논리삭제 시각 |

- 판매가는 `Product`에 두지 않는다(변형이 소유). 카탈로그 대표가는 ACTIVE 변형 가격에서 파생하는 조회이며 저장하지 않는다(집계·가격대 최적화는 범위 밖).

### 상태 (ProductStatus)

| 상태 | 의미 |
|---|---|
| ON_SALE | 판매중. 실제 노출·주문 가능은 ACTIVE 변형·재고에서 파생 |
| HIDDEN | 숨김. 그룹 전체 노출·주문 불가(관리자 일시 중지, 또는 변형·재고 시딩 전 미게시) |

- 전이: `ON_SALE ↔ HIDDEN`. 카탈로그 노출은 `ON_SALE` ∧ 미삭제 ∧ ACTIVE 변형 1개 이상일 때다. ACTIVE 변형이 0(전부 DISABLED·RETIRED)이면 제공할 것이 없어 노출하지 않는다(품절과 구분). 품절 표시는 ACTIVE 변형이 모두 소진(각 변형 재고가 `quantity = 0` 또는 `status ≠ SELLABLE`)일 때 파생하며 상품은 계속 노출한다(품절을 상품 상태로 두지 않는다). 이 노출·품절 파생은 상품·변형·재고를 읽는 앱 계층 합성이 담당한다(대표가 파생과 동일).

### Product 정책·불변식

- 등록(register): 필수값 name. 최초 상태 HIDDEN. 상품은 판매 가능한 변형·재고가 생기기 전엔 주문 가능하면 안 되므로, 앱 계층이 첫 변형 생성·재고 시딩·변형 활성화 성공 후 `show()`로 ON_SALE 전환한다(크로스 도메인 정책 참조).
- 노출 전환: `hide()`(ON_SALE→HIDDEN)·`show()`(HIDDEN→ON_SALE). HIDDEN은 관리자 일시 중지와 변형·재고 시딩 전 미게시를 겸한다(별도 DRAFT 상태를 두지 않아 죽은 상태를 늘리지 않는다).
- 주문 가능 조건은 상품 단독으로 정해지지 않는다: `ON_SALE` ∧ 미삭제 상품의 ACTIVE 변형 중 재고가 받쳐주는 것만 주문 라인에 담긴다(합성은 크로스 도메인 정책이 소유). HIDDEN·삭제 상품은 어떤 변형도 담기지 않는다.
- 상품명·설명 변경은 등록 후 가능하다(`rename`·`changeDescription`). 이미 생성된 주문은 `productName` 스냅샷을 보관하므로 편집이 주문 내역에 소급 영향이 없다.
- 논리삭제: `deletedAt`. 상품 소프트삭제는 변형을 연쇄 삭제하지 않는다(변형은 `deletedAt`이 없다). 삭제·HIDDEN 상품의 변형은 주문가능 합성의 상품 게이트가 거른다 — 담기·체크아웃이 변형의 상품 `ON_SALE`·미삭제를 검증하고, 카탈로그·장바구니 표시는 같은 파생을 읽기로 반영한다.

### Product 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | name, description? | 최초 HIDDEN | — |
| show | productId | HIDDEN→ON_SALE | 미존재; 잘못된 전이 |
| hide | productId | ON_SALE→HIDDEN | 미존재; 잘못된 전이 |
| rename | productId, newName | `name` 갱신, 기존 주문 무영향(productName 스냅샷) | 미존재 |
| changeDescription | productId, newDescription | `description` 갱신 | 미존재 |
| delete | productId | `deletedAt` 세팅(논리삭제) | 미존재 |
| getProduct(s) | productId(들) | 활성 상품(가격 미포함) | 미존재 |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

### ProductVariant 필드 (판매·재고 단위)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| productId | UUID | 필수 | 소속 상품 참조(루트 간 ID 참조) |
| price | Money(VO) | 필수 | 변형 판매가. 1 이상(무료 미지원) |
| status | ProductVariantStatus | 필수 | 아래 상태표. 최초 DISABLED |
| optionSignature | String | 필수 | 옵션 조합 정규화 키. 옵션 없으면 "". 아래 옵션 모델 |
| optionLabel | String | 선택 | 표시 라벨("Red / L"). 옵션 없으면 null |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 소프트삭제하지 않는다(은퇴는 RETIRED 상태). 낙관락(`@Version`)을 두지 않는다 — 가격·상태 변경은 관리자 저경합이라 기본(last-write-wins)을 따르고, 경합 민감한 수량 차감은 재고가 소유한다.

### 상태 (ProductVariantStatus)

| 상태 | 의미 |
|---|---|
| ACTIVE | 판매 제공(상품 ON_SALE·재고 SELLABLE·수량이면 주문 가능) |
| DISABLED | 카탈로그 제공 중단(되돌릴 수 있음). 재고 시딩 전 초기 상태이자 일시 중단 |
| RETIRED | 은퇴. 재제공·재가격·재전이 없는 종료 상태 |

- 전이: `ACTIVE ↔ DISABLED`, `{ACTIVE, DISABLED} → RETIRED`(종료). 최초 DISABLED — 재고 시딩 후 `enable`로 ACTIVE 전환한다(크로스 도메인 정책 참조). RETIRED는 완전 종료라 `changePrice`·`enable`·`disable`·`retire`를 모두 거부한다.
- 종료를 `deletedAt`이 아니라 status로 두는 것은 재고 `DISCONTINUED`와 같은 선택이다(소프트삭제하지 않는 루트의 종료). 조회가 은퇴 변형을 이력으로 반환해야 해 `deletedAt` 기반 소프트삭제 finder 규약을 피한다.
- DISABLED(카탈로그측 제공 중단)와 재고 `SOLD_OUT`(운영측 판매 보류)은 소유자·사유가 다른 두 축이다(상품 `ON_SALE`·재고 `SELLABLE`이 이미 별 축인 것과 동형). RETIRED(카탈로그 은퇴)와 재고 `DISCONTINUED`(재입고 없음)도 서로 다른 도메인이 기록하는 다른 종료 의도이며 상호 파생되지 않아 별도로 둔다.

### ProductVariant 정책·불변식

- 생성(create): 필수값 productId, price(≥1), 옵션 목록(선택). 최초 DISABLED. `(product_id, option_signature)`에서 비-RETIRED 변형이 유니크하다. 옵션 조합은 생성 시 확정·불변이며 조합 변경은 새 변형이다(add-only).
- 가격 변경(changePrice): newPrice ≥ 1. 이미 생성된 주문에는 영향이 없다(OrderLine이 단가를 스냅샷으로 보관). 장바구니는 변형 현재가를 체크아웃 시점에 조회한다. RETIRED는 거부.
- 활성/비활성/은퇴(enable·disable·retire): 관리자 카탈로그 생명주기이며 놓인 PENDING·PAID 주문을 소급하지 않고 신규 주문만 막는다.
- 유니크·재등록: 유니크는 부분(`status <> RETIRED`)이라 한 상품·한 옵션 조합에 비-RETIRED 변형이 최대 1개, RETIRED는 여럿 공존한다. 은퇴한 조합은 새 변형(새 variantId)으로 재등록할 수 있다(member.email 부분 유니크가 재가입을 허용하는 것과 같은 메커니즘). 생성 존재검사(옵션 시그니처 기준)는 인덱스 술어와 같은 `status <> RETIRED`로 맞춘다.
- 옵션 모델: 변형은 옵션을 optionSignature(유니크 키)와 optionLabel(표시)로 평탄 보관한다. 생성 입력(옵션명→값 목록)에서 파생·고정하며, 구조적 옵션 목록·상품 단위 옵션 타입 스키마·옵션 카탈로그는 두지 않는다(범위 밖).
- optionSignature 정규화: 옵션명·값을 trim·case-fold(유니코드 정규화 포함)하고, 옵션명·값은 변형 내 비어있지 않으며 옵션명은 유니크하고, 구분자 문자를 옵션명·값에서 금지하며(위반 시 예외), 옵션명 기준 정렬해 정규 문자열로 조인한다. 옵션이 없으면 "".
- optionLabel: 값을 입력 순서로 표시 구분자(" / ")로 조인하며 대소문자를 보존한다. 옵션이 없으면 null. 불변식 `optionSignature = "" ⟺ optionLabel = null`.
- 재고 관계: 변형당 재고는 최대 1행(재고 도메인 소유; 시딩 완료 후 1행). 은퇴는 재고 행을 건드리지 않는다(주문가능은 변형 ≠ ACTIVE로 이미 닫힘). 은퇴 조합을 재등록하면 새 변형에 새 재고 행이 생기고 옛 변형의 재고 행은 남는다(옛 주문 취소 복원용, "재고 행 소프트삭제 안 함"과 정합).

### ProductVariant 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | productId, price, options? | price ≥ 1, 비-RETIRED `(product_id, option_signature)` 유니크, 옵션명 유니크·비어있지 않음, 최초 DISABLED | 상품 미존재; 옵션 조합 중복(비-RETIRED); 가격 미달; 옵션 입력 오류 |
| enable | variantId | DISABLED→ACTIVE | 미존재; 잘못된 전이(RETIRED 포함) |
| disable | variantId | ACTIVE→DISABLED | 미존재; 잘못된 전이(RETIRED 포함) |
| changePrice | variantId, newPrice | newPrice ≥ 1, RETIRED 아님, 기존 주문 무영향(스냅샷) | 미존재; 가격 미달; RETIRED |
| retire | variantId | {ACTIVE, DISABLED}→RETIRED | 미존재; 이미 은퇴 |
| getVariant(s) | variantId(들) | 변형 조회 | 미존재 |
| getByProductId | productId | 상품의 변형 목록(없으면 빈 목록) | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 3. 재고 (stock)

변형별 재고 수량과 그 차감·복원을 소유한다. 동시 주문 경합이 실재하는 도메인이라 낙관락(`@Version`)을 둔다.

- 애그리거트 루트: `Stock`
- 스키마/테이블: `stock` / `stock`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| variantId | UUID | 필수 | 변형 참조(유니크 — 변형당 재고 1행) |
| quantity | int | 필수 | 가용 수량. 0 이상 |
| status | StockStatus | 필수 | 아래 상태표 |
| version | long | 필수 | 낙관락 버전(기본 0) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태 (StockStatus)

| 상태 | 의미 |
|---|---|
| SELLABLE | 판매 가능(수량이 있으면 주문 가능) |
| SOLD_OUT | 운영자 수동 판매 보류(수량이 남아도 주문 불가) |
| DISCONTINUED | 단종. 재입고·판매 재개 없음(종료 상태) |

- 전이: `SELLABLE ↔ SOLD_OUT`, `{SELLABLE, SOLD_OUT} → DISCONTINUED`(종료). 최초 상태 SELLABLE. 실제 소진(`quantity = 0`)은 상태값이 아니라 수량에서 파생 판정한다 — 자연 소진·재입고 대기(quantity=0), 수동 품절(SOLD_OUT), 단종(DISCONTINUED)은 회복 경로가 달라(재입고/판매 재개/회복 불가) 한 컬럼에 겹치지 않는다.

### 정책·불변식

- 생성(create): 각 변형에 초기 수량으로 1행 생성(변형 생성과 별개 호출). 변형당 재고는 최대 1행(variantId 유니크)이고 시딩 완료 후 정확히 1행이다. 초기 수량 0 이상, 최초 status SELLABLE.
- 차감(deduct, n): quantity ≥ n 일 때만 quantity -= n. 부족하면 재고부족 도메인 예외(주문 중단). 동시 차감은 낙관락으로 직렬화되어 오버셀이 없다(충돌 응답·재시도 메커니즘은 `docs/entity-persistence.md` 소유).
- 복원(restore, n): quantity += n. 주문 취소·결제 실패 보상에서 호출. status와 무관하게 항상 허용한다(SOLD_OUT·DISCONTINUED여도 물리 반환분을 복원). 복원은 가산이라 교환법칙이 성립하므로 낙관락 충돌 시 재시도해도 오버셀 위험이 없다(가산·재시도 안전, 멱등은 아님 — 크로스 도메인 정책 참조).
- 주문 가능(orderability) 합성은 재고 단독이 아니다: 상품 `ON_SALE`·미삭제 ∧ 변형 `ACTIVE` ∧ 재고 `status = SELLABLE` ∧ `quantity ≥ 주문 수량`이며, 이 합성은 크로스 도메인 정책(체크아웃)이 소유한다. 재고는 자기 status·수량만 소유한다 — SOLD_OUT·DISCONTINUED는 수량이 남아도 판매 불가이고, 판매성(`status = SELLABLE`) 검증은 체크아웃 게이트가, 수량 가드는 `deduct`가 소유한다.
- 상태 전이 비연쇄: `markSoldOut`·`markSellable`·`discontinue`는 이미 놓인 PENDING·PAID 주문을 소급하지 않고 신규 주문만 막는다.
- quantity는 음수가 될 수 없다(차감 가드가 보장).
- 재고 행은 소프트삭제하지 않는다. 변형이 은퇴(RETIRED)하거나 상품이 삭제돼도 복원이 항상 대상 행(variantId)을 찾으므로 취소·환불 보상이 성립한다.
- 재입고(increase, n): quantity += n. 신규 물량 입고로 취소 보상 반환(`restore`)과 별개 의도다. DISCONTINUED이면 거부(단종 재입고 불가). 자연 소진(`quantity = 0`, SELLABLE)이 영구 품절로 굳지 않게 한다.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | variantId, 초기수량 | variantId 유니크, 수량 ≥ 0, 최초 SELLABLE | variantId 중복 |
| markSoldOut | variantId | SELLABLE→SOLD_OUT | 미존재; 잘못된 전이 |
| markSellable | variantId | SOLD_OUT→SELLABLE | 미존재; 잘못된 전이 |
| discontinue | variantId | {SELLABLE, SOLD_OUT}→DISCONTINUED | 미존재; 이미 단종 |
| increase | variantId, n | quantity += n; DISCONTINUED이면 거부 | 미존재; 단종 |
| deduct | variantId, n | quantity ≥ n 일 때만 quantity -= n | 재고 부족 |
| restore | variantId, n | quantity += n (status 무관, 가산·재시도 안전, 멱등 아님) | — |
| getByVariantId | variantId | 변형당 재고 1행 | 미존재 |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 4. 장바구니 (cart)

회원의 구매 예정 변형 목록을 소유한다. 애그리거트 루트+자식(라인) 구조다.

- 애그리거트 루트: `Cart`, 자식: `CartItem`
- 스키마/테이블: `cart` / `cart`, `cart_item`

### Cart 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 회원 참조(유니크 — 회원당 장바구니 1개) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| items | Set\<CartItem\> | — | 자식 라인 집합 |

### CartItem 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| (cart) | Cart | 필수 | 부모 참조(애그리거트 내부 연관) |
| variantId | UUID | 필수 | 변형 참조. 한 장바구니 내 유니크 |
| quantity | int | 필수 | 1 이상 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 정책·불변식

- 회원당 장바구니 1개. 최초 담기 요청 시 없으면 생성한다(lazy get-or-create). 회원 가입과 결합하지 않는다.
- 담기 필수값: memberId, variantId, quantity(≥1).
- 담기 조건: 회원이 자격 활성(`status = ACTIVE`·미탈퇴)이고 변형이 ACTIVE이며 변형의 상품이 ON_SALE·미삭제일 것. 재고는 담기 시점에 검증하지 않는다(체크아웃 시점에 검증). 장바구니는 "구매 예정" 목록이다.
- 동일 변형 재담기: 새 라인을 만들지 않고 기존 라인 수량을 합산한다(variantId는 장바구니 내 유니크).
- 수량 변경: `changeItemQuantity`는 qty ≥ 1만 허용한다(qty < 1 거부). 수량을 늘릴 때(newQty > 현재)는 담기와 동일 자격 게이트(회원 자격 활성 ∧ 변형 ACTIVE ∧ 변형의 상품 ON_SALE·미삭제)를 적용하고, 줄이거나 유지는 게이트 없이 허용한다(수요 미증가라 정지·탈퇴 회원, 비활성·은퇴 변형·삭제 상품 라인의 정리도 가능). 라인 제거는 `removeItem` 전용 — 수량 인자에 삭제 의도를 겹치지 않는다.
- 제거·비우기: 라인 개별 제거, 전체 비우기(clear).
- 총액은 저장하지 않는다. 체크아웃 시 변형 현재가로 계산한다.
- 주문 전환: 체크아웃은 장바구니 전체를 주문으로 전환한다(부분 주문 미지원). 결제 완료 후 주문된 라인을 비운다(비우기는 `OrderPaid` 이벤트 소비로 처리 — 크로스 도메인 정책 참조).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| addItem | memberId, variantId, qty | get-or-create, 회원 자격 활성, 변형 ACTIVE·변형의 상품 ON_SALE·미삭제, 동일 변형 수량 합산, qty ≥ 1 | 변형 미존재; 주문 불가(변형 비활성·상품 HIDDEN·삭제); 회원 탈퇴 또는 정지 |
| changeItemQuantity | memberId, variantId, qty | qty ≥ 1로 설정; 증량 시 담기 게이트(회원 자격 활성·변형 ACTIVE·상품 ON_SALE·미삭제) | 라인 미존재; 수량 범위 미달(qty < 1); (증량 시) 회원 탈퇴·정지·주문 불가 |
| removeItem | memberId, variantId | 라인 제거 | 라인 미존재 |
| clear | memberId | 전체 라인 제거 | — |
| getCart | memberId | 회원당 장바구니 1개(없으면 빈 장바구니) | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 5. 쿠폰 (coupon)

할인 정책(쿠폰)과 회원에게 발급된 쿠폰을 소유한다. 정책과 발급분은 생명주기가 달라 서로 다른 애그리거트이고 ID로 잇는다.

- 애그리거트 루트: `Coupon`(정책), `IssuedCoupon`(발급분)
- 스키마/테이블: `coupon` / `coupon`, `issued_coupon`

### Coupon 필드 (정책)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 쿠폰명 |
| discount | Discount(VO) | 필수 | 할인 정책(정액/정률). 아래 값 객체 참조 |
| minOrderAmount | Money | 필수 | 최소 주문 금액. 0 가능 |
| validity | ValidityPeriod(VO) | 필수 | 발급 가능 기간(시작·종료). 아래 값 객체 참조 |
| usageValidDays | int | 필수 | 발급분 사용 창(일). 발급 시 `expiresAt = 발급시각 + usageValidDays`. 1 이상 |
| status | CouponStatus | 필수 | ACTIVE(발급 가능) 또는 DISABLED(발급 중지) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 값 객체 (Discount)

| 형 | 필드 | 제약·설명 |
|---|---|---|
| Fixed | amount: Money | 정액 할인. amount ≥ 1 |
| Rate | percent: int, maxCap: Money 선택 | 정률 할인. percent ∈ [1,100]. maxCap은 있으면 > 0(없으면 무제한). Fixed는 maxCap을 가질 수 없다 |

- sealed 2형(Fixed·Rate). 불법 상태(Fixed에 상한, percent 범위 밖, maxCap 0)를 타입·생성 검증으로 배제한다.
- 행위 `applyTo(orderAmount) → Money`: Fixed는 `min(amount, orderAmount)`. Rate는 `floor(orderAmount × percent / 100)`(곱한 뒤 나눔), maxCap 있으면 그 값으로 상한, 마지막에 `min(·, orderAmount)`로 clamp. 결과는 항상 주문금액 이하(결제금액 음수 불가).
- sealed 타입→컬럼 매핑(discriminator·값 컬럼)은 `docs/entity-persistence.md`가 소유한다.

### 값 객체 (ValidityPeriod)

| 필드 | 타입 | 제약·설명 |
|---|---|---|
| validFrom | Instant | 유효 시작 |
| validUntil | Instant | 유효 종료. `validFrom < validUntil`(엄격) |

- 불변식 `validFrom < validUntil`을 생성 시 강제한다. 행위 `contains(now) → boolean`으로 발급 가능 기간 판정을 한곳에 둔다(발급 창이지 사용 만료가 아니다 — 사용 만료는 `IssuedCoupon.expiresAt`).
- 다중 컬럼 `@Embeddable` 매핑은 `docs/entity-persistence.md`가 소유한다(Address와 동일 패턴).

### IssuedCoupon 필드 (발급분)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| couponId | UUID | 필수 | 쿠폰 정책 참조 |
| memberId | UUID | 필수 | 발급 대상 회원 참조 |
| status | IssuedCouponStatus | 필수 | ISSUED 또는 USED |
| expiresAt | Instant | 필수 | 사용 기한(발급 시 확정 = 발급시각 + 정책 `usageValidDays`) |
| usedAt | Instant | 선택 | 사용 시각 |
| orderId | UUID | 선택 | 사용된 주문 참조 |
| version | long | 필수 | 낙관락 버전(중복 사용 방지) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태

| CouponStatus | 의미 |
|---|---|
| ACTIVE | 발급 가능 |
| DISABLED | 발급 중지 |

| IssuedCouponStatus | 의미 |
|---|---|
| ISSUED | 발급됨. 사용 가능 |
| USED | 사용 완료 |

- 정책 전이: `ACTIVE ↔ DISABLED`. 의도 동사 `disable()`(ACTIVE→DISABLED)·`enable()`(DISABLED→ACTIVE)로만 전환한다. 발급 가능/중지는 쿠폰의 도메인 생명주기이며 호출자(관리자 등)의 유무와 무관하게 모델에 둔다.
- 발급분 전이: `ISSUED → USED`(사용), `USED → ISSUED`(취소 복원). 만료는 상태가 아니라 `validUntil` 경과로 파생 판정한다(죽은 상태·죽은 배치 없음).
- 발급분 불변식: `status == USED ⇔ usedAt ≠ null ∧ orderId ≠ null`.

### 정책·불변식

- 정책 생명주기(create·disable·enable): 쿠폰 정책은 `create`로 ACTIVE 생성하고, `disable`/`enable`로 발급 가능·중지를 전환한다.
- 발급(issue): 쿠폰이 ACTIVE이고 발급 가능 기간 내(`validity.contains(now)`)이며 대상 회원이 자격 활성(`status = ACTIVE`·미탈퇴)일 때만. 회원당 동일 쿠폰 1회 발급(`(couponId, memberId)` 유니크로 강제). 최초 상태 ISSUED. 발급 시 사용 기한 `expiresAt = 발급시각 + usageValidDays`를 확정(스냅샷). 정지·탈퇴 회원에게는 발급하지 않는다(담기·체크아웃 자격과 정책 일관).
- 발급 후 정책 변경 무영향: `use`는 `Coupon.status`를 재검사하지 않는다. 이미 발급된 쿠폰은 정책이 나중에 DISABLED가 돼도 계속 사용할 수 있다(발급분 자격만 검증). DISABLED는 신규 발급만 막고, 발급된 쿠폰 회수는 범위 밖이다.
- 할인 계산: `calculateDiscount`는 `discount.applyTo(주문금액)`에 위임한다(정액/정률 규칙·상한·주문금액 clamp은 Discount 값 객체가 소유).
- 적용 조건: (할인 전) 주문금액 ≥ minOrderAmount 이고 발급분이 ISSUED, 본인(memberId) 소유, 사용 기한 내(`now ≤ expiresAt`)이며, 산출 할인이 0보다 클 때만 적용한다(0 할인이면 1회 발급을 헛되이 소진하지 않도록 적용을 거부).
- 소유: 본인(memberId) 소유가 아닌 발급분은 존재 누출 방지로 미존재로 취급한다.
- 사용(use): 주문 생성 이후 `use(issuedCouponId, orderId)`로 USED 전이하고 usedAt·orderId 세팅. 동시 두 주문이 같은 쿠폰을 사용하면 낙관락으로 한쪽만 성공하고 다른 쪽은 충돌로 보상된다(직렬화 메커니즘은 `docs/entity-persistence.md` 소유).
- 만료: 발급분 사용 기한(`expiresAt`) 경과분은 사용 시점 검증에서 거부한다(별도 만료 상태·배치 없음). 정책 `validUntil`은 발급 가능 기간의 종료이지 사용 만료가 아니며, 정책 변경이 기발급분 사용 기한을 소급 바꾸지 않는다(`expiresAt` 스냅샷).
- 취소 복원(restoreUse): 주문 취소·결제 실패로 주문이 무효가 되면 USED → ISSUED로 복원하고 usedAt·orderId를 clear한다. 가드 전이라 USED가 아니면 no-op이다(중복 보상 무해).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | name, discount, minOrderAmount, validity, usageValidDays | 값 객체 불변식(Discount·ValidityPeriod), usageValidDays ≥ 1, 최초 ACTIVE | 불변식 위반 |
| disable | couponId | ACTIVE→DISABLED | 미존재; 잘못된 전이 |
| enable | couponId | DISABLED→ACTIVE | 미존재; 잘못된 전이 |
| issue | couponId, memberId | ACTIVE, 발급기간 내, 회원 자격 활성, `(couponId, memberId)` 유니크, 최초 ISSUED | 쿠폰 미존재; 발급 중지(DISABLED); 발급기간 밖; 회원 자격 비활성(정지·탈퇴); 중복 발급 |
| calculateDiscount | issuedCouponId, orderAmount | `discount.applyTo(orderAmount)`에 위임 | 미존재 |
| use | issuedCouponId, orderId | ISSUED·본인·사용 기한(`expiresAt`)·산출 할인 > 0 일 때만 USED 전이 | 미존재(미소유 포함); 상태·자격 위반; 만료; 낙관락 충돌 |
| restoreUse | issuedCouponId | 가드 USED→ISSUED(아니면 no-op), usedAt·orderId clear | — |
| getIssuedCoupon | issuedCouponId | 본인 발급분 1행 | 미존재(미소유 포함) |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 6. 주문 (order)

주문과 주문 라인을 소유한다. 라인은 주문 시점의 변형·상품명·옵션·단가를, 배송지는 주문 시점 값을 스냅샷으로 보관한다(상품·변형·회원 변경과 무관하게 내역을 보존). 정본 애그리거트 예시다.

- 애그리거트 루트: `Order`, 자식: `OrderLine`
- 스키마/테이블: `ordering` / `orders`, `order_line` (예약어 회피 divergence — 용어집 참조)

### Order 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| orderNumber | String | 필수 | 사람이 읽는 주문번호(활성 유니크). `place` 시 생성. UUIDv7 PK와 분리된 고객·CS·정산 참조용 업무 식별자 |
| memberId | UUID | 필수 | 주문 회원 참조 |
| status | OrderStatus | 필수 | 아래 상태표(결제 축) |
| fulfillmentStatus | FulfillmentStatus | 필수 | 아래 이행 상태표(물리 이행 축). 최초 NOT_STARTED |
| totalAmount | Money | 필수 | 라인 합계(할인 전) |
| discountAmount | Money | 필수 | 쿠폰 할인액. 0 가능. 서버가 쿠폰 정책으로 계산 |
| shippingFee | Money | 필수 | 배송비(0 가능). 체크아웃 입력 스냅샷 |
| payAmount | Money | 필수 | 결제 대상 금액 = totalAmount − discountAmount + shippingFee (≥ 0) |
| issuedCouponId | UUID | 선택 | 적용된 발급 쿠폰 참조 |
| shippingAddress | Address(VO) | 필수 | 배송지 스냅샷(다중 컬럼 `@Embeddable`) |
| shippedAt | Instant | 선택 | 출고 시각(SHIPPED 전 null) |
| deliveredAt | Instant | 선택 | 배송 완료 시각(DELIVERED 전 null) |
| paidAt | Instant | 선택 | 결제 완료 시각(`markPaid` 세팅) |
| cancelledAt | Instant | 선택 | 취소 시각(`cancel` 세팅) |
| cancellationReason | CancellationReason | 선택 | 취소 사유(`cancel` 세팅) |
| holdReason | HoldReason | 선택 | 이행 보류 사유. ON_HOLD에서만 존재(`holdFulfillment` 세팅, `releaseFulfillment` clear) |
| lines | Set\<OrderLine\> | 필수 | 1개 이상 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 값 객체 (Address, 다중 컬럼 `@Embeddable`)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| recipientName | String | 필수 | 수령인 |
| zipCode | String | 필수 | 우편번호 |
| roadAddress | String | 필수 | 도로명 주소 |
| detailAddress | String | 선택 | 상세 주소 |
| phone | String | 필수 | 수령 연락처 |

### OrderLine 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| (order) | Order | 필수 | 부모 참조(애그리거트 내부 연관) |
| variantId | UUID | 필수 | 변형 참조. 재고 복원·SKU 식별 키 |
| productId | UUID | 필수 | 상품 그룹 참조(자기완결 스냅샷) |
| productName | String | 필수 | 주문 시점 상품명 스냅샷 |
| optionLabel | String | 선택 | 주문 시점 변형 옵션 표시 스냅샷("Red / L"). 옵션 없으면 null |
| unitPrice | Money | 필수 | 주문 시점 변형 단가 스냅샷 |
| quantity | int | 필수 | 1 이상 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태 (OrderStatus)

| 상태 | 의미 |
|---|---|
| PENDING | 주문 생성됨. 결제 진행 중 |
| PAID | 결제 완료 |
| CANCELLED | 취소됨 |

- 전이: `PENDING → PAID`(결제 완료), `PENDING → CANCELLED`(결제 실패·쿠폰 확정 실패 보상), `PAID → CANCELLED`(결제 후 사용자 취소·환불). 발행 이벤트: PAID 전이 시 `OrderPaid`.

### 이행 상태 (FulfillmentStatus)

주문의 물리 이행 축이다. 결제 축(`OrderStatus`)과 직교하므로 별도 필드로 둔다(한 컬럼에 두 축을 겹치지 않는다).

| 상태 | 의미 |
|---|---|
| NOT_STARTED | 이행 시작 전(결제 완료 전, 또는 준비 대기) |
| PREPARING | 상품 준비 중 |
| ON_HOLD | 이행 보류(사기 심사·배송지 확인·입고 지연). 되돌릴 수 있음 |
| SHIPPED | 출고됨 |
| DELIVERED | 배송 완료 |

- 전이: `NOT_STARTED → PREPARING → SHIPPED → DELIVERED`, `PREPARING ↔ ON_HOLD`(`holdFulfillment`/`releaseFulfillment`). 최초 NOT_STARTED. `markPaid`가 PENDING→PAID와 함께 NOT_STARTED→PREPARING을 전이한다. `ship`은 PREPARING에서만 유효(보류분은 해제 후 출고). 배송 추적·배송사 연동은 범위 밖(단일 배송·단일 이행).
- 결제·이행 두 축은 저장상 직교하되 전이상 독립이 아니다: 모든 이행 전진은 `status == PAID`에서만 유효하고, 취소·미결제 주문의 `fulfillmentStatus`는 동결된다(취소 주문 출고 불가). `ON_HOLD ⇔ holdReason != null`(`FRAUD_REVIEW`·`ADDRESS_VERIFICATION`·`STOCK_DELAY`).

### 정책·불변식

- 생성(place): 라인 1개 이상. 회원은 자격 활성(`status = ACTIVE`·미탈퇴). 배송지 필수. `orderNumber` 생성. 최초 status PENDING, fulfillmentStatus NOT_STARTED.
- 각 라인은 주문 시점 변형·상품명·옵션 표시·단가(variantId·productId·productName·optionLabel·unitPrice)를, 배송지는 주문 시점 값을 복사한다(스냅샷). 자기완결 스냅샷이라 이후 상품·변형·회원 변경이 주문 내역을 바꾸지 않고, 이력 조회가 변형·상품으로 fan-out하지 않는다. variantId는 재고 복원·SKU 식별 키다.
- 금액 불변식: `Order.place`가 자기 라인에서 totalAmount = Σ(라인 unitPrice × quantity)를 계산한다. discountAmount는 서버가 쿠폰 정책으로 산출해 입력하되(클라이언트 전달값 불신), Order 루트가 `discountAmount ≤ totalAmount`와 `issuedCouponId != null ⟺ discountAmount > 0`(쿠폰 없는 유령 할인·쿠폰 있는 0원 할인 동시 배제)을 자기 강제한다(위반 시 도메인 예외). payAmount = totalAmount − discountAmount + shippingFee ≥ 0.
- price ≥ 1·quantity ≥ 1이라 totalAmount ≥ 1이 항상 성립한다(무료·0원 주문 없음). payAmount = 0은 전액 할인 ∧ shippingFee = 0일 때만이고(배송비가 있으면 payAmount > 0), 그 경우 결제는 PG를 생략하고 자동 승인한다.
- 결제 완료(markPaid): PENDING에서만 PAID로. 함께 fulfillmentStatus NOT_STARTED→PREPARING, `paidAt` 세팅. 전이 후 `OrderPaid`를 커밋 후 발행한다.
- 취소(cancel): PENDING 또는 PAID에서 CANCELLED로. PAID 취소는 `fulfillmentStatus ∈ {NOT_STARTED, PREPARING, ON_HOLD}`일 때만 허용하고 SHIPPED·DELIVERED면 거부한다(출고 이후는 반품 절차, 범위 밖). 전이 시 `cancelledAt`·`cancellationReason`(`PAYMENT_FAILED`·`STOCK_SHORTAGE`·`COUPON_CONFLICT`·`CUSTOMER_REQUEST`·`ADMIN_ACTION`)을 세팅한다. 전이는 1회만 유효하고, 후속 보상은 전이가 실제 일어난 호출에서만 태운다(중복 취소 무해). 보상 내용은 소스 상태에 따른다 — PENDING이면 재고·쿠폰 복원, PAID이면 환불 선행 후 재고·쿠폰 복원(크로스 도메인 정책 참조).
- 이행(ship·confirmDelivery·holdFulfillment·releaseFulfillment): 모든 이행 전진은 `status == PAID`에서만 유효하다(취소·미결제 주문은 fulfillment 동결). `ship`은 PREPARING→SHIPPED(+`shippedAt`), `confirmDelivery`는 SHIPPED→DELIVERED(+`deliveredAt`), `holdFulfillment`는 PREPARING→ON_HOLD(+`holdReason`: 사기 심사·배송지 확인·입고 지연), `releaseFulfillment`는 ON_HOLD→PREPARING(`holdReason` clear). 분할 배송·부분 이행·반품(RMA)은 범위 밖(단일 배송·단일 이행).
- 생성 후 라인 구성은 불변(수정 불가). 변경이 필요하면 취소 후 재주문.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| place | memberId, 라인 스냅샷, shippingAddress, discountAmount, shippingFee | 라인 ≥ 1, `orderNumber` 생성, totalAmount 자기 계산, discountAmount ≤ totalAmount, `issuedCouponId ⟺ discount>0`, payAmount = total−discount+shippingFee, 최초 PENDING·NOT_STARTED | 빈 주문(라인 0); 회원 탈퇴 또는 정지; 할인 초과 |
| markPaid | orderId | PENDING→PAID, 이행 NOT_STARTED→PREPARING, `paidAt` 세팅, `OrderPaid` 발행 | 미존재; 잘못된 전이 |
| cancel | orderId, reason | PENDING·PAID → CANCELLED 1회(PAID는 이행 NOT_STARTED·PREPARING·ON_HOLD만), `cancelledAt`·`cancellationReason` 세팅, 보상은 소스 상태 함수 | 미존재; 잘못된 전이; 출고 이후(SHIPPED·DELIVERED) |
| ship | orderId | `status == PAID` ∧ PREPARING→SHIPPED, `shippedAt` 세팅 | 미존재; 결제 축 PAID 아님(취소·미결제); 잘못된 전이 |
| confirmDelivery | orderId | `status == PAID` ∧ SHIPPED→DELIVERED, `deliveredAt` 세팅 | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| holdFulfillment | orderId, reason | `status == PAID` ∧ PREPARING→ON_HOLD, `holdReason` 세팅 | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| releaseFulfillment | orderId | `status == PAID` ∧ ON_HOLD→PREPARING, `holdReason` clear | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| getOrder | orderId | 주문 1행 | 미존재 |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 7. 결제 (payment)

주문 결제를 소유한다. 외부 PG 연동은 도메인이 소유한 벤더 중립 포트(`PaymentGateway`)로 하고, 실제 구현은 외부 어댑터가 담당한다.

- 애그리거트 루트: `Payment`
- 스키마/테이블: `payment` / `payment`
- 포트: `PaymentGateway`(approve·cancel). 구현은 외부 어댑터(연습용 stub).

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| orderId | UUID | 필수 | 주문 참조(유니크 — 주문당 결제 1행) |
| amount | Money | 필수 | 결제 금액 = 주문 payAmount |
| status | PaymentStatus | 필수 | 아래 상태표 |
| method | PaymentMethod | 선택 | 결제 수단(동기 승인 수단: CARD·EASY_PAY·BANK_TRANSFER). `amount > 0`일 때만 존재(불변식) |
| failureReason | FailureReason | 선택 | FAILED 사유. approve 실패 시 세팅 |
| pgTransactionId | String | 선택 | PG 승인 거래 ID(승인 후 채워짐) |
| pgCancelTransactionId | String | 선택 | PG 취소(환불) 거래 ID. cancel이 PG 환불 호출 시 세팅 |
| approvedAt | Instant | 선택 | 승인 시각(approve 성공 시 세팅) |
| cancelledAt | Instant | 선택 | 취소·환불 시각(cancel 세팅) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 낙관락 없음. 주문당 결제 1행(orderId 유니크)이라 동시 경합이 없고, 재요청은 상태 가드로 막는다.

### 상태 (PaymentStatus)

| 상태 | 의미 |
|---|---|
| REQUESTED | 결제 요청됨 |
| APPROVED | 승인 완료 |
| FAILED | 승인 실패 |
| CANCELLED | 취소·환불됨 |

- 전이: `REQUESTED → APPROVED`, `REQUESTED → FAILED`, `APPROVED → CANCELLED`(환불).

### 정책·불변식

- 요청(request): 주문당 결제 1행(orderId 유니크). amount = 주문 payAmount. 최초 REQUESTED.
- 승인: `PaymentGateway.approve` 성공 시 APPROVED + pgTransactionId, 실패 시 FAILED + `failureReason`(`INSUFFICIENT_BALANCE`·`LIMIT_EXCEEDED`·`INVALID_METHOD`·`RISK_DECLINED`·`GATEWAY_ERROR`). `payAmount == 0`(전액 할인)이면 PG를 호출하지 않고 APPROVED로 자동 처리한다(pgTransactionId 없음). 체크아웃 파사드가 동기 반환된 사유로 보상·안내를 분기한다.
- 취소·환불(cancel): APPROVED에서만 CANCELLED로. `PaymentGateway.cancel` 호출 시 취소 거래 ID를 `pgCancelTransactionId`에 저장한다(승인 ID `pgTransactionId`와 분리 — 정산·CS 대사). 단 `pgTransactionId == null`(PG 미호출 승인)이면 환불 호출을 생략하고 상태만 CANCELLED로 둔다.
- 멱등: 이미 결제된 주문(APPROVED)에 재요청은 상태 가드로 거부. 결제 실패로 주문이 취소되면 재체크아웃은 새 주문(새 orderId)을 만든다 — 실패 결제 행은 새 주문을 막지 않는다.
- 결제 수단: 승인/실패가 동기 확정되는 수단만(`CARD`·`EASY_PAY`·`BANK_TRANSFER`). 불변식 `amount > 0 ⇔ method != null` — `amount == 0`(전액 할인) 결제는 실 지불 수단이 없으므로 `method` 없이 PG 생략 자동 승인하고 가짜 `CARD`를 채우지 않는다. 가상계좌 등 입금 대기·비동기 통지 수단은 범위 밖(실 PG·웹훅 기준선 밖). 수단별 부가 데이터(할부·provider)는 단일 상세 컬럼으로 겸용하지 않는다.
- 도메인 이벤트 없음. 결제 결과는 동기 stub이 호출자(체크아웃 파사드)에게 즉시 반환하므로, 승인/실패 반영은 파사드가 동기로 처리한다(크로스 도메인 정책 참조).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| request | orderId, amount, method? | orderId 유니크, amount = 주문 payAmount, `amount>0 ⇔ method≠null`, 최초 REQUESTED | 주문당 결제 중복 |
| approve | paymentId | REQUESTED→APPROVED(+pgTransactionId·`approvedAt`) 또는 FAILED(+failureReason); `payAmount == 0`이면 PG 생략 자동 승인 | 미존재; 잘못된 전이; 이미 승인된 결제 재요청 |
| cancel | paymentId | APPROVED→CANCELLED(+`cancelledAt`); PG 환불 시 `pgCancelTransactionId` 세팅; `pgTransactionId == null`이면 PG 생략 | 미존재; 잘못된 전이 |

포트 `PaymentGateway`(approve·cancel)는 도메인이 소유하고 구현은 외부 어댑터다. 반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 크로스 도메인 정책 (체크아웃·취소)

여러 도메인을 잇는 흐름의 정책이다. 조율은 앱 계층(파사드)이 하고, 각 도메인 서비스는 자기 트랜잭션만 소유한다(파사드는 트랜잭션을 열지 않는다). 결제가 동기 stub이라 결과를 파사드가 즉시 알므로, 핵심 정합은 파사드의 동기 조율 + 실패 시 동기 보상으로 처리하고, 유실돼도 무해한 후처리(장바구니 비우기) 하나만 도메인 이벤트로 뺀다.

### 체크아웃 (주문 생성 → 결제)

전진 경로(파사드, 동기, 각 서비스 자기 트랜잭션):

1. 검증(읽기): 회원 자격 활성(`status = ACTIVE`·미탈퇴); 장바구니가 비어 있지 않을 것; 각 라인의 변형 ACTIVE ∧ 변형의 상품 ON_SALE·미삭제 ∧ 재고 `status = SELLABLE`이고 수량이 주문 수량 이상; 쿠폰이 있으면 본인·ISSUED·사용 기한(`expiresAt`)·(할인 전)최소주문금액·산출 할인 > 0 충족. 상품 ON_SALE·변형 ACTIVE(카탈로그 가시성의 그룹·SKU 두 수준)과 재고 SELLABLE(재고 가용성)은 서로 다른 게이트다. 변형·상품·재고 조회의 부재·삭제·미시딩은 예외가 아니라 주문 불가로 강등한다. 할인액·payAmount 계산. 어느 라인이든 주문 불가·재고 부족이면 전체 체크아웃을 거부한다(부분 주문 없음). 문제 라인은 자동 제거하지 않으며 사용자가 장바구니에서 직접 뺀다.
2. 주문 생성(PENDING): 파사드가 각 라인을 `variantId`→변형(단가·optionLabel·productId)→상품(상품명)으로 해소해 스냅샷을 만들고(단가는 변형 현재가로 서버가 채운다 — 클라이언트 값 불신), 배송지 스냅샷·서버 계산 할인과 함께 `place`에 넘긴다. `place`(order 도메인)는 변형을 로드하지 않는다(1TX 1애그리거트). Order가 totalAmount 자기 계산·discount ≤ total 가드. orderId 확정. 이 PENDING 주문이 이후 부작용의 사가 앵커다.
3. 재고 차감: 라인별 `deduct(variantId, qty)`. 어느 라인이든 실패하면 앞서 차감한 라인을 복원 + 주문 취소(→CANCELLED) 후 중단.
4. 쿠폰 확정(있으면): `use(issuedCouponId, orderId)` → ISSUED→USED. 실패(동시 사용 등) 시 재고 복원 + 주문 취소 후 중단.
5. 결제: `payAmount == 0`이면 PG 생략·자동 APPROVED. 아니면 `PaymentGateway.approve`.
   - APPROVED → 결제 APPROVED 기록, `OrderProcessor.markPaid`(PENDING→PAID).
   - FAILED/예외 → 동기 보상: 재고 복원 + 쿠폰 복원(USED→ISSUED) + 주문 취소(→CANCELLED). 결제 FAILED 기록.
6. 주문 PAID 커밋 후 → `OrderPaid` 발행 → `CartRemover.clear`(주문된 라인) 리스너(멱등).

- 주문 앵커: 주문 PENDING을 재고 차감 전에 만들어, 차감·쿠폰 사용 등 모든 부작용이 조회 가능한 orderId에 걸린다. 차감 후 크래시가 나도 유실이 관측·복구 가능하다(주문 없는 차감이 아니다).
- 보상은 단일 소유다: 결제 성공 전 모든 실패(재고·쿠폰·결제)는 파사드가 그 콜스택에서 동기 보상한다. 이벤트 리스너와 파사드가 같은 실패를 경쟁 보상하는 이원화가 없다.
- 유일한 도메인 이벤트는 `OrderPaid`(주문이 커밋 후 발행) → 장바구니 비우기다. 비우기는 정합성 비필수라 최종일관성이 정당한 유일 지점이다(유실 시 장바구니가 남아 재체크아웃이 같은 상품을 재주문할 수 있으나 사용자 개시·가시).

### 취소·환불 (결제 후)

사용자가 PAID 주문을 취소하는 흐름(파사드, 동기):

- 이중 가드: 결제 취소(`Payment.cancel`, 가드 APPROVED→CANCELLED 1회)로 환불을 먼저 하고, 성공 시 주문 취소(`Order.cancel`, 가드 PAID→CANCELLED 1회)가 재고 복원(라인 `variantId`로) + 쿠폰 복원(USED→ISSUED)을 게이트한다. `pgTransactionId == null`(PG 미호출 승인) 결제는 환불 호출을 생략한다.
- 환불이 복원의 선행조건이다: 환불 실패 시 주문은 PAID로 남고 복원하지 않는다(클라이언트 재시도). 이는 보상 코레오그래피이며 무손실 보장은 범위 밖이다.

### 상품 등록 → 첫 변형·재고 시딩

상품 등록과 첫 변형·초기 재고 생성을 잇는 흐름(파사드, 동기):

- 상품 `register`(HIDDEN) → 변형 `create(productId, price, 옵션?)`(DISABLED) → 재고 `create(variantId, 초기수량)` → 변형 `enable`(ACTIVE) → 상품 `show()`(ON_SALE). 첫 변형이 상품의 옵션을 싣는다(옵션 없는 상품만 "" 기본 변형이라 옵션 상품에 유령 기본 변형이 생기지 않는다).
- 변형을 DISABLED로 먼저 두는 이유: 담기 게이트에 재고 검사가 없어 재고 없는 ACTIVE 변형이 카탈로그·담기에 노출되는 것을 막는다. `enable`은 재고 존재를 검증하지 않고 시딩 순서가 보장하며, 체크아웃의 부재→주문 불가 강등이 방어적 심층이다.
- 파괴적 보상이 없다: 실패 시 HIDDEN 상품·DISABLED 변형·재고가 남아 재시도로 복구한다(삭제 보상 없음). 중단 복구는 기존 변형에서 남은 단계를 재개한다(재고 미생성이면 `create`, 미활성이면 `enable`) — DISABLED 변형은 비-RETIRED라 같은 옵션으로 변형 `create`를 다시 부르면 유니크에 막히므로 재생성이 아니라 재개다. Product·ProductVariant·Stock 세 루트에 걸쳐 원자적이지 않다.
- 추가 변형(이미 ON_SALE 상품): 변형 `create`(DISABLED) → 재고 `create(variantId, 초기수량)` → `enable`. enable 전까지 주문 불가라 재고 없는 ACTIVE 변형 창이 없다.
- 변형은 재고 파트너가 있고 나서만 ACTIVE가 되므로, 주문 가능 조건 판정(재고 조회 포함)이 재고 없는 ACTIVE 변형을 만나지 않는다.

### 회원 탈퇴 가드 (미배송 주문)

회원 탈퇴 요청을 처리하는 흐름(파사드, 동기):

- 파사드가 `OrderReader`로 해당 회원의 미배송 PAID 주문(status = PAID ∧ fulfillmentStatus ≠ DELIVERED ∧ 미취소) 존재를 확인하고, 있으면 탈퇴를 거부한다. 없을 때만 `MemberRemover.delete`를 호출한다. PENDING 주문은 막지 않는다(결제 전이라 방치 가능).
- 이는 앱 계층 오케스트레이션 정책이지 member 도메인 불변식이 아니다 — member는 order에 컴파일 의존하지 않으므로(빌드 강제), 크로스 도메인 규칙은 파사드가 소유한다. 아웃바운드 포트가 아니라 파사드가 `OrderReader`를 읽어 게이트한다.
- 탈퇴 비연쇄(회원 §1)와 양립한다 — 탈퇴가 주문을 연쇄 정리하지 않되, 미배송 주문이 있으면 탈퇴 자체를 선행 거부할 뿐이다.

### 정합·보장 수준

- 결제 이후 핵심 상태(PAID/CANCELLED)는 파사드가 동기로 확정하므로 "결제됐는데 PENDING" 같은 창이 없다.
- in-process 이벤트(`OrderPaid`)는 커밋 후 발행하되 무손실 보장이 아니다. 리스너는 멱등하고, 유실 시 장바구니가 남는 것 외 영향이 없다.
- 복원 정확성: 모든 재고·쿠폰 복원은 주문의 1회성 `PENDING·PAID → CANCELLED` 전이가 게이트한다. `restore`는 가산·교환법칙이라 낙관락 충돌 시 재시도해도 안전하지만 멱등은 아니므로(커밋된 복원을 재전달하면 이중 가산), 이 전이 가드가 정확히-1회 복원을 보장한다.
- 크로스 트랜잭션 부분 실패(예: 보상 도중 프로세스 크래시)의 무손실 복구는 범위 밖이다. 낙관락 충돌 재시도 수렴(가산·재시도 안전)과 크래시 리플레이 멱등(제공 안 함)은 구분된다.

### 도메인 이벤트 명세 (OrderPaid)

| 필드 | 타입 | 설명 |
|---|---|---|
| orderId | UUID | 결제 완료된 주문 |
| memberId | UUID | 소비자가 비울 장바구니를 특정 |
| orderedVariantIds | Set\<UUID\> | 비울 라인의 변형들 |

- 최소 페이로드다: 소비자(`CartRemover.clear`)가 필요로 하는 것만 담는다. `orderedVariantIds`는 체크아웃과 소비 사이에 사용자가 담은 라인을 보존하기 위함이다(부분 주문 미지원이라 그 외엔 전체 비우기와 같다). 소비 의미는 주문된 variantId의 라인 전체 제거(수량 인지 아님).
- 발행 시점: 주문 PAID 전이 커밋 후. 커밋 전 발행은 롤백 시 장바구니를 잘못 비우므로 fail-safe 방향으로 커밋 후에 낸다.
- 멱등: 집합 제거는 이미 제거된 라인 재삭제가 no-op이라 구조적으로 멱등이다(별도 처리 이력 불필요).
- 이벤트 record 구조·발행 포트·커밋 후 발행 배선·멱등 소비 키 메커니즘은 `docs/architecture.md`가 소유한다.

## 상태 전이 요약

| 도메인 | 상태 흐름 |
|---|---|
| member | ACTIVE ↔ SUSPENDED. 탈퇴는 deletedAt |
| product(그룹) | ON_SALE ↔ HIDDEN. 논리삭제 deletedAt |
| product(변형) | ACTIVE ↔ DISABLED, {ACTIVE, DISABLED} → RETIRED. 최초 DISABLED |
| stock | SELLABLE ↔ SOLD_OUT, {SELLABLE, SOLD_OUT} → DISCONTINUED. 소진은 수량 파생 |
| cart | 상태 없음 |
| coupon(정책) | ACTIVE ↔ DISABLED |
| coupon(발급분) | ISSUED ↔ USED (취소 시 복원) |
| order(결제) | PENDING → PAID → CANCELLED, PENDING → CANCELLED |
| order(이행) | NOT_STARTED → PREPARING → SHIPPED → DELIVERED, PREPARING ↔ ON_HOLD |
| payment | REQUESTED → APPROVED → CANCELLED, REQUESTED → FAILED |

## 도메인 용어집 (예약어 divergence)

코드마다 갈리지 않도록 표준 divergence를 등재한다. 아래 외 divergence는 임의로 만들지 않는다.

| 도메인 개념 | 엔티티 | 스키마 | 테이블 | 사유 |
|---|---|---|---|---|
| 주문 | `Order` | `ordering` | `orders` | `order`는 SQL 예약어. 스키마·테이블 모두 회피 |
| 주문 라인 | `OrderLine` | `ordering` | `order_line` | — |

나머지 6개 도메인(member·product·stock·cart·coupon·payment)은 예약어가 아니라 스키마·테이블이 도메인/엔티티명과 일치한다(product 스키마는 `product`·`product_variant` 두 테이블을 두며 둘 다 divergence가 없다).

## 영속·마이그레이션 유의 (구현 시 규칙이 강제)

도메인 모델 확정 사항 중 빌드·기동이 강제하는 항목이다. 구현 계획이 이를 반영한다.

- `@Version` 컬럼: `stock`·`issued_coupon`에 `version BIGINT DEFAULT 0`을 Flyway로 추가한다. `payment`·`product_variant`는 `@Version`이 없으므로 version 컬럼을 두지 않는다(`ddl-auto=validate` 정합).
- 논리 FK 인덱스: 모든 `xxx_id` 컬럼(product_variant.product_id, stock.variant_id, cart.member_id, cart_item.variant_id, orders.member_id·issued_coupon_id, order_line.variant_id·product_id, issued_coupon.coupon_id·member_id·order_id, payment.order_id) 인덱스를 Flyway로 생성한다(물리 FK 없음).
- 유니크 제약: member.email(부분 유니크 `WHERE deleted_at IS NULL`), product_variant(product_id, option_signature)(부분 유니크 `WHERE status <> 'RETIRED'` — 은퇴 조합 재등록 허용), stock.variant_id, cart.member_id, cart_item(cart_id, variant_id), issued_coupon(coupon_id, member_id), payment.order_id, orders.order_number를 Flyway로 강제한다. product_variant 부분 유니크 술어가 enum 문자열 `'RETIRED'`를 참조하므로 상태값을 바꿀 때 인덱스 술어도 함께 갱신한다.
- 스키마 등록: 7개 도메인 스키마 각각을 `db/migration/{name}/`에 두고 `SchemaFlywayFactory`(common-jpa)에 등록한다.
- 애그리거트 내부 연관(cart_item→cart, order_line→order)도 물리 FK 없이 `NO_CONSTRAINT`로 매핑한다.

## 확정된 결정 (요약)

앞선 설계·검증 리뷰를 반영한 결정이다.

- 회원: `MemberStatus{ACTIVE, SUSPENDED}`(정지/해제 `suspend`/`reinstate`, 정지 사유 `suspensionReason`), 표시이름 변경 `rename`, 탈퇴는 deletedAt(status 아님) + `withdrawalReason`, 자격 활성 = ACTIVE ∧ 미탈퇴, 가입 즉시 ACTIVE, 이메일 부분 유니크로 재가입 허용, 탈퇴·정지 비연쇄, 미배송 PAID 주문 있으면 탈퇴 거부(파사드 정책).
- 상품: 카탈로그 그룹(가격 미소유), 등록 시 HIDDEN → 첫 변형·재고 시딩·변형 활성화 후 `show()`로 ON_SALE, `hide`/`show` 의도 동사, 상품명·설명 편집(`rename`/`changeDescription`, 주문 스냅샷 무영향), 시딩은 앱 계층이 순차 조율(HIDDEN-first, 파괴적 보상 없음).
- 상품변형(SKU): product 도메인 독립 루트(productId로 연결), 판매·재고 단위. 가격 소유(≥1, `changePrice`), `ProductVariantStatus{ACTIVE, DISABLED, RETIRED}`(최초 DISABLED, 재고 시딩 후 `enable`, RETIRED 완전 종료), 옵션은 평탄 optionSignature·optionLabel(생성 시 불변), `(product_id, option_signature)` 부분 유니크(`status <> RETIRED`)로 은퇴 조합 재등록 허용, add-only·삭제 없음(deletedAt·@Version 없음).
- 재고: `StockStatus{SELLABLE, SOLD_OUT, DISCONTINUED}`(수동 품절·단종을 quantity=0과 분리), 변형당 1행(variant_id 유니크), 재입고 `increase`, 주문가능 = 상품 ON_SALE·미삭제 ∧ 변형 ACTIVE ∧ 재고 SELLABLE ∧ 수량(합성은 체크아웃), 즉시 차감(예약 모델 아님), 차감은 낙관락 가드·판매성은 체크아웃 게이트, 복원은 status 무관 가산·재시도 안전(멱등 아님, 전이 게이트로 1회 복원).
- 쿠폰: 할인은 `Discount` 값 객체(sealed Fixed/Rate, 알고리즘·불변식 내재), `ValidityPeriod`는 발급 가능 기간, 발급분 사용 기한은 `IssuedCoupon.expiresAt`(발급시각 + `usageValidDays`)로 발급창과 분리, 정책 상태 ACTIVE↔DISABLED(`disable`/`enable`)로 발급 가능·중지, 발급은 ACTIVE·발급기간·회원당 1회, 사용은 주문 생성 이후 확정(정책 status 재검사 없음 — 발급분 자격만), 산출 할인 0이면 적용 거부, 만료는 `expiresAt` 판정(EXPIRED 상태 없음), 취소 시 복원.
- 주문: 라인(variantId·productId·productName·optionLabel·unitPrice)·배송지 스냅샷, `orderNumber`, 결제 축 status와 별도 이행 축 `fulfillmentStatus{NOT_STARTED→PREPARING→SHIPPED→DELIVERED, PREPARING↔ON_HOLD}`(`ship`/`confirmDelivery`/`holdFulfillment`/`releaseFulfillment`, markPaid가 PREPARING 전이, 이행 전진은 status=PAID에서만 — 취소 주문 출고 불가), 보류 사유 `holdReason`, 출고 후 취소 금지, `paidAt`·`cancelledAt`·`cancellationReason` 시각·사유, 배송비 `shippingFee`(payAmount = total−discount+shippingFee), 불변식 `issuedCouponId ⟺ discountAmount>0`, totalAmount 자기 계산·discountAmount ≤ totalAmount 자기 강제, 할인·payAmount 서버 계산, 취소는 PENDING·PAID 모두(보상은 소스 상태 함수).
- 결제: `@Version` 없음, 주문당 1행, `PaymentMethod{CARD, EASY_PAY, BANK_TRANSFER}`(동기 수단, `amount>0 ⇔ method≠null`), FAILED 사유 `failureReason`, 승인·취소 거래 ID 분리(`pgTransactionId`/`pgCancelTransactionId`), 업무 시각 `approvedAt`/`cancelledAt`, 0원은 PG 생략 자동 승인, 도메인 이벤트 없음.
- 정합: 핵심은 파사드 동기 조율 + 동기 보상, 주문 PENDING을 사가 앵커로 먼저 생성(order-first), 유일 이벤트는 `OrderPaid → 장바구니 비우기`.
- 표기: 각 도메인에 오퍼레이션 카탈로그(연산·입력·강제 불변식·거부[도메인 표현])를 두고, 반환 형상·ErrorCode·HTTP status·동시성 메커니즘은 `docs/`가 소유(참조).
- Money: `common-core` 순수 값 타입, 컨버터는 `common-jpa` 단일. 배송지 `Address`는 `@Embeddable`.

## 명시적 범위 밖

- 배송 추적·배송사(택배) 연동·운송장 번호(이행 상태 SHIPPED·DELIVERED 자체는 범위 내).
- 부분 취소·부분 환불·부분 선택 주문.
- 회원 주소록(배송지는 체크아웃 요청이 매번 제공).
- 인증·로그인(JWT), 회원 자격증명.
- 실 PG·비동기 웹훅과 그에 따른 PENDING 리컨실·아웃박스(내구성 메시징).
- 쿠폰 선착순 발급 한도.
- 발급된 쿠폰의 회수·관리자 무효화(정책 DISABLED는 신규 발급만 막고 기발급분은 계속 사용 가능).
- 상품변형 옵션의 구조 정규화: 옵션을 별도 엔티티·상품 단위 옵션 타입 스키마·글로벌 옵션 카탈로그·파셋 검색으로 두는 것(현재는 optionSignature·optionLabel 평탄 저장). 변형 간 옵션 타입 정합도 강제하지 않는다.
- 상품 대표가·가격대 집계 읽기모델과 그 최적화(대표가는 ACTIVE 변형에서 파생하는 조회다).
- 상품당 총재고 집계(변형 재고 합산 — admin/read-model).
- 상인용 SKU 코드(`orderNumber` 같은 업무 식별자) — 변형 식별은 variantId로 충분하다.
- 동시 "PENDING 주문 취소" 경로(최소 흐름의 주문 상태 전이는 체크아웃 스레드가 단독 소유). 동시 취소 경로 도입 시 필요한 동시성 제어는 `docs/entity-persistence.md`가 소유한다.
- 체크아웃 동시 더블서밋 방어(멱등 필터는 추후 `common-web` 도입 시).

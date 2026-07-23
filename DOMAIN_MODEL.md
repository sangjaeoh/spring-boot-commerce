# 커머스 도메인 모델 (Domain Model)

이 문서는 커머스 최소 구현의 도메인 모델을 소유한다 — 어떤 도메인이 있고, 각 도메인이 어떤 엔티티·필드·상태·정책·불변식·오퍼레이션을 갖는지. 즉 "실제로 무엇을 만드는가"를 필드 수준까지 고정한다.

- 요구사항(*무엇을*)은 [`REQUIREMENTS.md`](./REQUIREMENTS.md)가 소유한다. 이 문서는 그 요구사항을 실현하는 모델을 소유한다.
- 설계 규칙(ID·연관·상태 전이·소프트삭제 등)은 `docs/architecture.md`·`docs/entity-persistence.md`·`docs/coding-conventions.md`를 따른다. 이 문서는 그 규칙을 이 앱의 10개 도메인에 적용한 결과다.
- 모듈 구조·빌드 순서 같은 "어떻게 세우는가"는 이 문서의 범위가 아니다(아키텍처 규칙 문서 `docs/`가 소유).

범위는 10개 도메인: 회원(member) · 상품(product) · 재고(stock) · 장바구니(cart) · 쿠폰(coupon) · 주문(order) · 결제(payment) · 위시리스트(wishlist) · 리뷰(review) · 문의(inquiry).

기준선(이 문서의 전제): 결제 게이트웨이는 실패·응답 유실을 시뮬레이션하는 동기 승인 fake이고 응답 유실 결제는 배치 앱의 리컨실·웹훅 확정 경로가 PG 거래 상태 조회로 확정한다, 도메인 이벤트는 트랜잭션 아웃박스를 경유한다(발행-커밋 원자, 배치 릴레이가 최소 1회 전달), 인증은 이메일+패스워드 로그인·JWT 액세스 토큰과 리프레시 토큰 발급·구매자 셀프서비스의 토큰 주체 강제·역할(BUYER/ADMIN) 기반 관리자 오퍼레이션 가드까지(경계·표면은 [`REQUIREMENTS.md`](./REQUIREMENTS.md)).

## 공통 규약

- 식별자: 모든 엔티티 PK는 앱에서 생성하는 UUIDv7. `@GeneratedValue` 없음.
- 크로스 도메인·애그리거트 참조: 순수 `UUID xxxId` 값만 보관한다. 물리 FK·객체 연관 없음(같은 애그리거트 내부만 객체 연관).
- 시각: `createdAt`·`updatedAt`은 JPA Auditing이 채운다(엔티티가 직접 선언하지 않음).
- 삭제: 논리삭제 기본. 삭제 지원 엔티티는 nullable `deletedAt`을 두고 활성 조회에서 제외한다. 일부 엔티티(회원 배송지·상품 이미지·위시리스트 찜·리뷰 본인 삭제)는 물리 삭제다 — 각 도메인 절이 명시한다.
- 금액: `Money`(원 단위 정수, 0 이상). 여러 도메인이 공유하는 값 객체라 `domain-shared`가 `Money`와 JPA 변환기(`MoneyConverter`)를 함께 소유한다. 통화는 KRW 단일로 가정한다.
- 수량: 정수. 별도 명시가 없으면 1 이상.
- 낙관락: 기본으로 두지 않는다. 실 경합이 있는 재고 차감·쿠폰 사용과, 복원을 게이트해 동시 중복이 이중 복원·이중 환불이 되는 주문 취소·환불/결제 취소 전이, 그리고 동시 합산이 유실되는 장바구니 라인에만 `@Version`을 둔다.
- 상태: enum(문자열 저장). 상태 변경은 의도 동사 메서드 + 허용 전이 가드로만 한다(setter 없음). 상태는 실세계 전이·정책으로 정당화되면 선제적으로 둔다(소비자가 아직 없어도 근거 있는 현실 상태는 모델에 둔다). 근거 없이 아무도 전이시키지 않는 "죽은 상태"만 피한다.
- 표기: 아래 각 도메인 필드 표는 이 공통 필드도 함께 명시한다 — 모든 엔티티는 `id`·`createdAt`·`updatedAt`을 가지며, 해당 도메인에 한해 `deletedAt`·`version`을 갖는다.

## 공용 값 객체 (Money)

`Money`는 여러 도메인(product·coupon·order·payment)이 공유하는 값 객체라 `domain-shared`가 소유한다. 도메인 전용 값 객체(`Email`·`Address`)는 각 도메인 절에 명시한다.

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| amount | long | 필수 | 원 단위 금액. 0 이상. 단일 컬럼 매핑(`AttributeConverter`) |

- 불변 값이고 `plus`·`minus`·`multiply`로 새 값을 만든다(뺄셈 결과가 음수면 예외). 통화는 KRW 단일.

## 1. 회원 (member)

회원의 식별과 자격증명(패스워드 해시)·역할, 그리고 회원 배송지 주소록을 소유한다. 자격증명 검증(이메일+패스워드)은 이 도메인이 담당하고, JWT 액세스·리프레시 토큰 발급·검증과 인증 표면(로그인·토큰 갱신·비밀번호 재설정·이메일 인증의 토큰 발급·유효기간·메일 발송)은 앱 경계가 담당한다(경계·표면은 `REQUIREMENTS.md` 인증 소유).

- 애그리거트 루트: `Member`, `MemberAddress`(같은 도메인의 별도 루트 — `memberId` 값 참조로 소속 표시)
- 스키마/테이블: `member` / `member`, `member_address`

### Member 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| email | Email(VO) | 필수 | 활성 회원 중 유니크. 형식 검증(VO). 식별 키 |
| name | String | 필수 | 표시 이름 |
| passwordHash | String | 필수 | bcrypt 해시(60자). Info·응답에 비노출 |
| role | MemberRole | 필수 | `BUYER`·`ADMIN`. 가입은 항상 BUYER, ADMIN은 설정 기반 기동 시딩으로만 생성. 생성 후 불변(변경 오퍼레이션 없음) |
| status | MemberStatus | 필수 | 아래 상태표 |
| suspensionReason | SuspensionReason | 선택 | 정지 사유. SUSPENDED에서만 존재, `reinstate` 시 clear |
| emailVerifiedAt | Instant | 선택 | 이메일 소유 인증 시각(`verifyEmail` 세팅). 자격·로그인 판정에 쓰지 않는 참고 필드 |
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

- 가입(register): 필수값은 email, name, password. 가입 즉시 주문·장바구니를 쓸 수 있다(별도 활성화 절차 없음).
- 패스워드 정책: 8자 이상 72바이트(bcrypt 입력 한계) 이하. bcrypt 해시로만 저장하고 평문·해시를 도메인 경계 밖(Info·응답)으로 내보내지 않는다.
- 자격증명 검증(authenticate): 미탈퇴 회원의 해시 일치로 검증한다. 미존재·탈퇴·불일치는 같은 거부(401)로 응답해 계정 존재를 노출하지 않는다 — 탈퇴 이메일은 재가입 가능하므로 탈퇴분은 조회에서부터 제외된다. 정지 회원은 검증을 통과한다(로그인 허용) — 차단은 담기·체크아웃 자격 게이트가 담당한다(탈퇴·정지 비연쇄 원칙과 동일 축).
- 이메일 유니크: 활성 회원 사이에서 유니크. 탈퇴한 이메일은 재가입 가능 — DB에서 부분 유니크 인덱스(`WHERE deleted_at IS NULL`)로 강제한다.
- 이메일 인증: `emailVerifiedAt`은 이메일 소유 인증 시각을 기록하는 참고 필드다. 자격 활성 판정·`authenticate`·담기·체크아웃 게이트 어디에도 이 필드를 조건으로 쓰지 않는다 — 이메일 인증은 회원 자격에 영향을 주지 않는다.
- 비밀번호 변경·재설정: `replacePassword`는 현재 비밀번호 해시 대조 후 새 비밀번호로 교체하고, `resetPassword`는 본인 확인(재설정 토큰 소비)이 앱 경계에서 선행돼 현재 비밀번호 대조 없이 교체한다. 두 경로 모두 가입과 같은 패스워드 정책을 강제하고 bcrypt로 재해시 저장한다.
- 탈퇴(delete): `deletedAt`·`withdrawalReason`(`NO_LONGER_USED`·`PRIVACY_CONCERN`·`DISSATISFIED`·`SWITCHED_SERVICE`) 세팅(논리삭제). 탈퇴 회원은 기본 조회에서 제외.
- 주문 자격: 담기·주문은 자격 활성 회원만 — `status = ACTIVE` 이고 미탈퇴(`deletedAt IS NULL`). 체크아웃·담기에서 검증.
- 활성 두 축 분리: 영속 활성(`deletedAt IS NULL`, SUSPENDED 포함 — 운영이 정지 회원을 조회·`reinstate`) vs 자격 활성(`status = ACTIVE`, 담기·체크아웃 자격). 조회는 정지 회원을 포함하고 자격 검증만 정지를 차단한다.
- 탈퇴·정지 비연쇄: 탈퇴·정지해도 회원의 장바구니·발급 쿠폰·PENDING 주문은 그대로 남고, 다음 체크아웃·담기의 자격 검증에서만 차단한다(연쇄 정리 없음).

### Member 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | email, name, password | 활성 회원 사이 이메일 유니크, 이메일 형식, 패스워드 정책(8자 이상 72바이트 이하), bcrypt 해시 저장 | 이메일 중복; 이메일 형식 오류; 패스워드 형식 오류 |
| authenticate | email, password | 미탈퇴 회원 + 해시 일치(정지 회원 통과) | 자격증명 불일치(미존재·탈퇴·불일치 동일 거부) |
| suspend | memberId, reason | ACTIVE→SUSPENDED, `suspensionReason` 세팅 | 미존재; 잘못된 전이 |
| reinstate | memberId | SUSPENDED→ACTIVE, `suspensionReason` clear | 미존재; 잘못된 전이 |
| rename | memberId, newName | `name` 갱신(`email` 불변) | 미존재 |
| replacePassword | memberId, currentRawPassword, newRawPassword | 미탈퇴 회원 + 현재 해시 일치, 새 패스워드 정책(8자 이상 72바이트 이하), bcrypt 재해시 저장 | 미존재; 현재 패스워드 불일치; 새 패스워드 형식 오류 |
| resetPassword | memberId, newRawPassword | 미탈퇴 회원, 현재 대조 없이 교체(본인 확인은 재설정 토큰 소비가 선행 — 앱 경계 소유), 패스워드 정책 동일 | 미존재; 새 패스워드 형식 오류 |
| verifyEmail | memberId | `emailVerifiedAt`을 현재 시각으로 기록(자격 무영향) | 미존재 |
| delete | memberId, reason | `deletedAt`·`withdrawalReason` 세팅(논리삭제) | 미존재 |
| getMember | memberId | 활성 회원 1행(SUSPENDED·suspensionReason 포함) | 미존재 |

반환 형상(명령/조회)·거부의 예외 매핑은 `docs/coding-conventions.md`가 소유. 서비스 역할 배치·네이밍도 같은 문서가 소유한다.

### MemberAddress 필드 (배송지 주소록)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 소유 회원. 순수 값 참조(물리 FK 없음) |
| recipientName | String | 필수 | 수령인 이름 |
| zipCode | String | 필수 | 우편번호. 형식 검증 없음(자유 문자열) |
| roadAddress | String | 필수 | 도로명 주소 |
| detailAddress | String | 선택 | 상세 주소 |
| phone | String | 필수 | 수령인 연락처. 형식 검증 없음 |
| defaultAddress | boolean | 필수 | 기본 배송지 여부. 회원당 최대 1개(부분 유니크 인덱스로 DB 강제) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 상태 없음. 삭제는 물리 삭제다(`deletedAt` 없이 하드 delete).

### MemberAddress 정책·불변식

- 등록 한도: 회원당 최대 10개. 초과 등록은 거부한다.
- 최초 등록: 첫 배송지는 자동으로 기본 배송지가 된다(카운트 0일 때만 `defaultAddress = true`로 생성).
- 기본 단일성: `(member_id) WHERE is_default` 부분 유니크 인덱스로 DB가 강제한다. 기본 재지정은 기존 기본을 먼저 해제한 뒤 새 대상을 지정한다(같은 대상 재지정은 no-op). 기본 배송지는 항상 최대 1개다.
- 삭제 시 기본 승계 없음: 기본 배송지를 삭제해도 다른 배송지가 자동 승격되지 않는다(회원이 다시 지정해야 한다).
- 수정(revise)은 배송지 정보만 바꾸고 기본 지정 여부를 바꾸지 않는다.
- 접근 범위: 모든 조회·수정·삭제·기본 지정은 `(addressId, memberId)` 스코프로 조회하고, 없으면(타인 소유 포함) 미존재로 취급한다(존재 누출 방지).
- 주소록은 체크아웃과 연결되지 않는다 — 체크아웃은 매 요청 배송지 전체를 입력받는다(배송지 참조 방식 미지원. 요구 배경은 `REQUIREMENTS.md` 소유).

### MemberAddress 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| add | memberId, recipientName, zipCode, roadAddress, detailAddress?, phone | 회원당 최대 10개, 첫 등록은 기본 자동 지정 | 등록 한도 초과 |
| getAddresses | memberId | 기본 우선·최신 등록순 정렬 목록(없으면 빈 목록) | — |
| revise | addressId, memberId, recipientName, zipCode, roadAddress, detailAddress?, phone | 본인 소유만, 기본 여부 불변 | 미존재(타인 소유 포함) |
| designateDefault | addressId, memberId | 기존 기본 해제 후 지정, 기본은 항상 1개 | 미존재(타인 소유 포함) |
| purge | addressId, memberId | 본인 소유만, 물리 삭제 | 미존재(타인 소유 포함) |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 2. 상품 (product)

판매 상품의 카탈로그 그룹(`Product`)과 그 판매·재고 단위인 상품변형(`ProductVariant`), 상품 분류(`Category`), 상품 이미지(`ProductImage`)를 소유한다. 카탈로그 정보는 `Product`가, 판매가·옵션은 변형이 소유하고, 재고 수량은 별도 재고 도메인이 소유한다. 그룹과 변형은 생명주기가 달라(변형은 시간차 추가·개별 비활성/은퇴·개별 가격) 서로 다른 애그리거트이고 `UUID productId`로 잇는다(coupon 정책·발급분과 같은 두 루트 구조). `Category`·`ProductImage`도 각각 독립 루트이며 `Product`와 ID 참조로만 잇는다.

- 애그리거트 루트: `Product`(카탈로그 그룹), `ProductVariant`(판매·재고 단위), `Category`(상품 분류), `ProductImage`(상품 이미지)
- 스키마/테이블: `product` / `product`, `product_variant`, `category`, `product_image`

### Product 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 상품명 |
| description | String | 선택 | 상세 설명 |
| categoryId | UUID | 선택 | 카테고리 참조(루트 간 ID 참조, 물리 FK 없음). 없으면 미분류 |
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

- 전이: `ON_SALE ↔ HIDDEN`. 카탈로그 노출은 `ON_SALE` ∧ 미삭제 ∧ ACTIVE 변형 1개 이상일 때다. ACTIVE 변형이 0(전부 DISABLED·RETIRED)이면 제공할 것이 없어 노출하지 않는다(품절과 구분). 품절 표시는 ACTIVE 변형이 모두 소진(각 변형 재고가 `quantity = 0` 또는 `status ≠ SELLABLE`)일 때 파생하며 상품은 계속 노출한다(품절을 상품 상태로 두지 않는다). 이 노출·품절 파생은 상품·변형·재고를 읽는 앱 계층 합성이 담당한다(대표가 파생과 동일). 공개 상품 상세도 같은 상품 게이트(`ON_SALE` ∧ 미삭제)로 걸러 숨김·삭제 상품을 미존재와 같은 404로 은닉한다(존재 누출 방지). 관리자 상품 조회 표면은 숨김을 포함한다.

### Product 정책·불변식

- 등록(register): 필수값 name. 최초 상태 HIDDEN. 상품은 판매 가능한 변형·재고가 생기기 전엔 주문 가능하면 안 되므로, 앱 계층이 첫 변형 생성·재고 시딩·변형 활성화 성공 후 `show()`로 ON_SALE 전환한다(크로스 도메인 정책 참조).
- 카테고리 지정: 등록·편집 시 `categoryId`를 지정할 수 있다(선택, 상품당 최대 1개). 지정 시점에 활성 카테고리(`deletedAt IS NULL`) 존재를 검증하고, 없으면 거부한다. 생략하거나 null로 편집하면 미분류다.
- 노출 전환: `hide()`(ON_SALE→HIDDEN)·`show()`(HIDDEN→ON_SALE). HIDDEN은 관리자 일시 중지와 변형·재고 시딩 전 미게시를 겸한다(별도 DRAFT 상태를 두지 않아 죽은 상태를 늘리지 않는다).
- 주문 가능 조건은 상품 단독으로 정해지지 않는다: `ON_SALE` ∧ 미삭제 상품의 ACTIVE 변형 중 재고가 받쳐주는 것만 주문 라인에 담긴다(합성은 크로스 도메인 정책이 소유). HIDDEN·삭제 상품은 어떤 변형도 담기지 않는다.
- 상품명·설명 변경은 등록 후 가능하다(`rename`·`changeDescription`). 이미 생성된 주문은 `productName` 스냅샷을 보관하므로 편집이 주문 내역에 소급 영향이 없다.
- 논리삭제: `deletedAt`. 상품 소프트삭제는 변형을 연쇄 삭제하지 않는다(변형은 `deletedAt`이 없다). 삭제·HIDDEN 상품의 변형은 주문가능 합성의 상품 게이트가 거른다 — 담기·체크아웃이 변형의 상품 `ON_SALE`·미삭제를 검증하고, 카탈로그·장바구니 표시는 같은 파생을 읽기로 반영한다.
- 카탈로그 검색·정렬: 카탈로그 목록 조회는 상품명 키워드(부분 일치·대소문자 무시)·카테고리 필터와 정렬 기준(최신 등록순·대표가 오름차순·대표가 내림차순)을 조합한다. 정렬 기준이 노출 집합(페이지 크기·총건수)을 바꾸지 않는다 — 세 조회 모두 같은 노출 술어(`ON_SALE` ∧ 미삭제 ∧ ACTIVE 변형 존재 ∧ 키워드 ∧ 카테고리)를 쓰고 정렬만 다르다. 대표가(가격 정렬 기준)는 정렬 시점에 노출 집합 안에서 ACTIVE 변형 가격의 최소값으로 파생한다 — 별도 캐시·집계 테이블이 없다(대표가 비저장 결정과 일관).

### Product 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | name, description?, categoryId? | 최초 HIDDEN, categoryId 있으면 활성 카테고리 존재 | 카테고리 미존재(활성 아님) |
| show | productId | HIDDEN→ON_SALE | 미존재; 잘못된 전이 |
| hide | productId | ON_SALE→HIDDEN | 미존재; 잘못된 전이 |
| rename | productId, newName | `name` 갱신, 기존 주문 무영향(productName 스냅샷) | 미존재 |
| changeDescription | productId, newDescription | `description` 갱신 | 미존재 |
| assignCategory | productId, categoryId? | `categoryId` 갱신(null이면 미분류 해제), 있으면 활성 카테고리 존재 | 미존재; 카테고리 미존재(활성 아님) |
| delete | productId | `deletedAt` 세팅(논리삭제) | 미존재 |
| getProduct(s) | productId(들) | 활성 상품(가격 미포함) | 미존재 |
| getExposedPage | keyword?, categoryId?, pageable | 노출 집합(`ON_SALE` ∧ 미삭제 ∧ ACTIVE 변형 1개 이상)을 최신 등록순으로, keyword가 있으면 상품명 부분 일치(대소문자 무시)로, categoryId가 있으면 소속 일치로 좁힘 | — |
| getExposedPageOrderByPriceAsc | keyword?, categoryId?, pageable | 노출 집합을 대표가(ACTIVE 변형 최저가) 오름차순, 동가면 최신순으로 정렬. 키워드·카테고리 의미는 getExposedPage와 동일 | — |
| getExposedPageOrderByPriceDesc | keyword?, categoryId?, pageable | 노출 집합을 대표가 내림차순, 동가면 최신순으로 정렬. 키워드·카테고리 의미는 getExposedPage와 동일 | — |
| getPage | pageable | 관리자용 페이지 — 상태 무관(삭제만 제외), 숨김 포함 | — |

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
- 상인용 SKU 코드(`orderNumber` 같은 업무 식별자)는 두지 않는다 — 변형 식별은 `variantId`로 충분하다(범위 밖).
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
| getByProductIds | productId들 | 여러 상품의 변형을 배치 조회(카탈로그 페이지 합성용) | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

### Category 필드 (상품 분류)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 카테고리 이름. 유니크 제약 없음(중복 이름 허용) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 논리삭제 시각 |

- 상태 없음. 계층이 없다 — 상위·하위 분류(parentId)·트리 구조를 두지 않는 단일 레벨 평면 목록이다.

### Category 정책·불변식

- 생성(create): 필수값 name. 이름 유니크 제약이 없어 동명 카테고리가 여럿 존재할 수 있다.
- 논리삭제: `deletedAt`. 이 카테고리를 참조하는 `Product.categoryId`를 연쇄 정리하지 않는다 — 참조 상품은 관리자가 `assignCategory`로 재지정하거나 null로 해제하기 전까지 삭제된 카테고리 ID를 그대로 보관한다(참조 무결성은 애플리케이션이 강제하지 않음, `product.category_id`에 물리 FK 없음).
- `Product`와의 연결: `Product.categoryId`는 선택(nullable)이고 등록·편집 시점에 활성 카테고리(`deletedAt IS NULL`) 존재를 검증한다. 미검증 상태로 넘어가는 경로는 없다.

### Category 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | name | 최초 활성 | — |
| rename | categoryId, newName | `name` 갱신 | 미존재 |
| delete | categoryId | `deletedAt` 세팅(논리삭제), 참조 상품 무영향 | 미존재 |
| getCategories | — | 활성 카테고리 전체를 이름순으로 반환(없으면 빈 목록) | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

### ProductImage 필드 (상품 이미지)

이미지 바이트는 별도 스토리지 포트(`ImageStore`)가, 메타(키·URL·정렬 순서)는 이 엔티티가 소유한다.

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| productId | UUID | 필수 | 소속 상품 참조(루트 간 ID 참조) |
| storageKey | String | 필수 | 스토리지 보관 키. 삭제 시 스토리지 제거에 쓴다 |
| url | String | 필수 | 공개 URL. 업로드 시점에 스토리지가 반환한 값을 그대로 저장 |
| sortOrder | int | 필수 | 정렬 순서. 0부터 업로드 순, 상품당 현재 최댓값+1로 채번. 최솟값이 대표 이미지 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 상태 없음. 소프트삭제하지 않는다(`deletedAt` 없음) — 삭제는 행 자체를 지우는 물리삭제다.

### ProductImage 정책·불변식

- 업로드(append): 상품이 미삭제 상태로 존재해야 한다. 콘텐츠 타입은 jpeg·png·webp만 허용하고, 바이트 크기는 5MB 이하여야 한다. 상품 존재·형식·크기 검증 후 스토리지에 먼저 저장하고(트랜잭션 밖 부작용), 메타 행만 트랜잭션으로 영속한다 — 메타 영속이 실패하면 스토리지에 고아 파일이 남는 트레이드오프를 수용한다.
- 정렬 순서: 상품별 현재 `sortOrder` 최댓값 + 1로 채번(첫 업로드는 0). 순서를 바꾸는 연산은 없다(재정렬 미지원). 최솟값이 대표 이미지다.
- 삭제(purge): 상품·이미지 ID로 행을 찾아 먼저 트랜잭션 내에서 행을 지우고, 커밋 후 스토리지에서 제거한다 — 스토리지 제거 실패는 고아 파일만 남기고 행 삭제를 되돌리지 않는다.
- 개수 제한: 상품당 업로드 가능한 이미지 수 제한을 두지 않는다.
- 이미지가 소속 상품의 노출 상태(`ON_SALE`/`HIDDEN`)나 카탈로그 게이트에 영향을 주지 않는다(이미지 유무는 노출 조건이 아니다).

### ProductImage 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| append | productId, contentType, content(byte[]) | 상품 미삭제 존재, 콘텐츠 타입이 jpeg/png/webp, 5MB 이하, sortOrder = 현재 최댓값+1 | 상품 미존재; 지원하지 않는 형식; 5MB 초과 |
| purge | productId, imageId | 행 삭제 + 스토리지 제거(커밋 후) | 이미지 미존재(상품 소속 불일치 포함) |
| getByProductId | productId | 정렬 순서 오름차순 목록(없으면 빈 목록) | — |
| getByProductIds | productId들 | 여러 상품의 이미지를 배치 조회(카탈로그 대표 이미지 합성용) | — |

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
- 재입고(increase, n): quantity += n. 신규 물량 입고로 취소 보상 반환(`restore`)과 별개 의도다. DISCONTINUED이면 거부(단종 재입고 불가). 자연 소진(`quantity = 0`, SELLABLE)이 영구 품절로 굳지 않게 한다. 이 오퍼레이션은 `StockRestocked` 이벤트를 발행하지 않는다.
- 재입고 이벤트 발행(`StockRestocked`): `markSellable`(SOLD_OUT→SELLABLE) 전이 성공 시 단 한 번, 같은 트랜잭션 안에서 아웃박스 행으로 발행한다 — "품절이었다가 다시 살 수 있게 된" 전환이 곧 알림 대상 재입고다. 수량 증량(`increase`)은 이 이벤트를 발행하지 않는다. "재입고"라는 한 단어가 두 오퍼레이션(수량 증가 `increase` / 상태 전환 `markSellable`)에 걸쳐 쓰이므로 서술은 오퍼레이션명으로 구분한다. 이벤트 페이로드·소비는 도메인 이벤트 명세 절이 소유한다.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | variantId, 초기수량 | variantId 유니크, 수량 ≥ 0, 최초 SELLABLE | variantId 중복 |
| markSoldOut | variantId | SELLABLE→SOLD_OUT | 미존재; 잘못된 전이 |
| markSellable | variantId | SOLD_OUT→SELLABLE, 전이 성공 시 `StockRestocked` 발행(같은 트랜잭션 아웃박스 기록) | 미존재; 잘못된 전이 |
| discontinue | variantId | {SELLABLE, SOLD_OUT}→DISCONTINUED | 미존재; 이미 단종 |
| increase | variantId, n | quantity += n; DISCONTINUED이면 거부; `StockRestocked` 미발행 | 미존재; 단종 |
| deduct | variantId, n | quantity ≥ n 일 때만 quantity -= n | 미존재; 재고 부족 |
| restore | variantId, n | quantity += n (status 무관, 가산·재시도 안전, 멱등 아님) | 미존재 |
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
| version | long | 필수 | 낙관락(`@Version`) — 동일 라인 동시 수량 합산의 유실 방지 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 정책·불변식

- 회원당 장바구니 1개. 최초 담기 요청 시 없으면 생성한다(lazy get-or-create). 회원 가입과 결합하지 않는다.
- 담기 필수값: memberId, variantId, quantity(≥1).
- 담기 조건: 회원이 자격 활성(`status = ACTIVE`·미탈퇴)이고 변형이 ACTIVE이며 변형의 상품이 ON_SALE·미삭제일 것. 재고는 담기 시점에 검증하지 않는다(체크아웃 시점에 검증). 장바구니는 "구매 예정" 목록이다.
- 동일 변형 재담기: 새 라인을 만들지 않고 기존 라인 수량을 합산한다(variantId는 장바구니 내 유니크).
- 동시 담기 경합: 최초 담기의 장바구니·라인 중복 생성(유니크 위반)은 담기 서비스가 재조회-재시도 한 번으로 합산에 수렴시키고, 동일 라인 동시 합산은 낙관락(`@Version`)이 유실을 막는다 — 진 쪽은 충돌로 끝난다(충돌 응답·재시도 규약은 `docs/entity-persistence.md` 소유).
- 수량 변경: `changeItemQuantity`는 qty ≥ 1만 허용한다(qty < 1 거부). 수량을 늘릴 때(newQty > 현재)는 담기와 동일 자격 게이트(회원 자격 활성 ∧ 변형 ACTIVE ∧ 변형의 상품 ON_SALE·미삭제)를 적용하고, 줄이거나 유지는 게이트 없이 허용한다(수요 미증가라 정지·탈퇴 회원, 비활성·은퇴 변형·삭제 상품 라인의 정리도 가능). 라인 제거는 `removeItem` 전용 — 수량 인자에 삭제 의도를 겹치지 않는다.
- 제거·비우기: 라인 개별 제거(`removeItem`), 주문된 변형 라인 일괄 제거(`removeItems` — 결제 완료 이벤트 소비 전용 도메인 오퍼레이션, 사용자 표면 없음), 전체 비우기(`clear`).
- 총액은 저장하지 않는다. 체크아웃 시 변형 현재가로 계산한다.
- 표시 파생: 뷰는 라인별 주문 가능(orderable)을 카탈로그와 같은 합성(변형 ACTIVE ∧ 상품 ON_SALE·미삭제 ∧ 재고 SELLABLE·수량 1 이상, IN 배치 조회)으로 읽기 반영한다. 주문 불가 라인은 소계 표시는 유지하되 총액에서 제외한다 — 체크아웃이 전량 거부할 금액을 총액으로 보이지 않게 하고, 라인 정리는 사용자 소유다(자동 제거 없음).
- 주문 전환: 체크아웃은 장바구니 전체 또는 선택한 라인을 주문으로 전환한다(선택 라인만 골라 체크아웃할 수 있고, 장바구니를 거치지 않는 바로구매도 있다 — 크로스 도메인 정책 참조). 결제 완료 후 주문된 변형 라인을 `removeItems`로 제거한다 — 발행은 주문 PAID 커밋과 원자적인 아웃박스 행 기록이고, 전달은 별도 배치 앱의 릴레이가 재발행한 `OrderPaid`를 리스너가 소비한다(체크아웃 응답 시점에는 아직 비워지지 않을 수 있는 비동기 후처리). 전달은 최소 1회(at-least-once)이며 재시도 상한 초과분은 dead letter로 격리돼 자동 소비되지 않는다(운영 개입 필요). 소비는 `removeItems`의 집합 제거가 구조적으로 멱등이라 중복 전달을 흡수한다(전달 메커니즘은 도메인 이벤트 명세·`docs/architecture.md` 참조).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| addItem | memberId, variantId, qty | get-or-create(중복 생성 경합은 재조회-재시도), 회원 자격 활성, 변형 ACTIVE·변형의 상품 ON_SALE·미삭제, 동일 변형 수량 합산, qty ≥ 1 | 변형 미존재; 주문 불가(변형 비활성·상품 HIDDEN·삭제); 회원 탈퇴 또는 정지; 낙관락 충돌(동일 라인 동시 합산) |
| changeItemQuantity | memberId, variantId, qty | qty ≥ 1로 설정; 증량 시 담기 게이트(회원 자격 활성·변형 ACTIVE·상품 ON_SALE·미삭제) | 라인 미존재; 수량 범위 미달(qty < 1); (증량 시) 회원 탈퇴·정지·주문 불가 |
| removeItem | memberId, variantId | 라인 제거 | 라인 미존재 |
| removeItems | memberId, variantIds | 주어진 변형 라인 일괄 제거(없는 라인 무시) | — |
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
| maxIssuance | Integer | 선택 | 총 발급 한도. 없으면 무제한. 있으면 1 이상 |
| issuedCount | int | 필수 | 소진 카운트. 기본 0. 원자적 조건부 UPDATE로만 증가 |
| status | CouponStatus | 필수 | ACTIVE(발급 가능) 또는 DISABLED(발급 중지) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 값 객체 (Discount)

| 형 | 필드 | 제약·설명 |
|---|---|---|
| Fixed | amount: Money | 정액 할인. amount ≥ 1 |
| Rate | percent: int, maxCap: Money 선택 | 정률 할인. percent ∈ [1,100]. maxCap은 있으면 > 0(없으면 무제한). Fixed는 maxCap을 가질 수 없다 |

- 판별 형 2종(Fixed·Rate)을 가진 단일 값 객체다. 형은 `type` 판별값으로 구분하고, 불법 조합(Fixed에 상한, percent 범위 밖, maxCap 0)을 생성 검증(compact constructor)으로 배제한다.
- 행위 `applyTo(orderAmount) → Money`: Fixed는 `min(amount, orderAmount)`. Rate는 `floor(orderAmount × percent / 100)`(곱한 뒤 나눔), maxCap 있으면 그 값으로 상한, 마지막에 `min(·, orderAmount)`로 clamp. 결과는 항상 주문금액 이하(결제금액 음수 불가).
- 판별 유니온 VO의 컬럼 매핑(형 판별 enum + 형별 nullable 값 컬럼)은 `docs/entity-persistence.md`가 소유한다.

### 값 객체 (ValidityPeriod)

| 필드 | 타입 | 제약·설명 |
|---|---|---|
| validFrom | Instant | 유효 시작 |
| validUntil | Instant | 유효 종료. `validFrom < validUntil`(엄격) |

- 불변식 `validFrom < validUntil`을 생성 시 강제한다. 행위 `isValidAt(now) → boolean`으로 발급 가능 기간 판정을 한곳에 둔다(발급 창이지 사용 만료가 아니다 — 사용 만료는 `IssuedCoupon.expiresAt`).
- 다중 컬럼 `@Embeddable` 매핑은 `docs/entity-persistence.md`가 소유한다(Address와 동일 패턴).

### IssuedCoupon 필드 (발급분)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| couponId | UUID | 필수 | 쿠폰 정책 참조 |
| memberId | UUID | 필수 | 발급 대상 회원 참조 |
| status | IssuedCouponStatus | 필수 | ISSUED, USED 또는 REVOKED |
| expiresAt | Instant | 필수 | 사용 기한(발급 시 확정 = 발급시각 + 정책 `usageValidDays`) |
| usedAt | Instant | 선택 | 사용 시각 |
| orderId | UUID | 선택 | 사용된 주문 참조 |
| revokedAt | Instant | 선택 | 무효화 시각 |
| revokeReason | String | 선택 | 무효화 사유(자유 문자열 한 필드) |
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
| REVOKED | 관리자 무효화. 사용 불가·재전이 없는 종료 상태 |

- 정책 전이: `ACTIVE ↔ DISABLED`. 의도 동사 `disable()`(ACTIVE→DISABLED)·`enable()`(DISABLED→ACTIVE)로만 전환한다. 발급 가능/중지는 쿠폰의 도메인 생명주기이며 호출자(관리자 등)의 유무와 무관하게 모델에 둔다.
- 발급분 전이: `ISSUED → USED`(사용), `USED → ISSUED`(취소 복원), `ISSUED → REVOKED`(무효화). 만료는 상태가 아니라 `expiresAt` 경과로 파생 판정한다(죽은 상태·죽은 배치 없음).
- 발급분 불변식: `status == USED ⇔ usedAt ≠ null ∧ orderId ≠ null`, `status == REVOKED ⇔ revokedAt ≠ null ∧ revokeReason ≠ null`.

### 정책·불변식

- 정책 생명주기(create·disable·enable): 쿠폰 정책은 `create`로 ACTIVE 생성하고, `disable`/`enable`로 발급 가능·중지를 전환한다.
- 발급(issue): 쿠폰이 ACTIVE이고 발급 가능 기간 내(`validity.isValidAt(now)`)이며 대상 회원이 자격 활성(`status = ACTIVE`·미탈퇴)일 때만. 회원당 동일 쿠폰 1회 발급(`(couponId, memberId)` 유니크로 강제). 발급 한도(`maxIssuance`)가 있으면 소진 시 거부한다. 최초 상태 ISSUED. 발급 시 사용 기한 `expiresAt = 발급시각 + usageValidDays`를 확정(스냅샷). 정지·탈퇴 회원에게는 발급하지 않는다(담기·체크아웃 자격과 정책 일관).
- 발급 한도 동시성: 한도는 정책 행의 원자적 조건부 UPDATE(`issued_count = issued_count + 1 where issued_count < max_issuance`)로 강제한다. DB 행 락이 동시 발급을 직렬화해 경합에서도 한도 초과가 불가능하고, 0행 갱신은 결정적 소진 거부(409, 재시도 무의미)로 매핑된다.
  - 재고 차감의 낙관락+409(클라이언트 재시도)는 기각했다 — 낙관락은 경합이 예외적일 때의 패턴인데 선착순 한도는 설계상 단일 정책 행에 버스트가 집중돼 재시도 폭주가 되고, 한도 소진은 일시적 충돌이 아니라 정상 종결 상태(마감)라 재시도 유도 신호가 어울리지 않으며, 정책 행 버전 충돌에 무제한 정책 발급·관리자 중지/재개까지 끌려들어간다.
  - COUNT 쿼리 검사는 기각했다 — READ COMMITTED에서 검사와 삽입 사이 경합 창이 초과 발급을 허용한다.
  - 카운트 선점(UPDATE)과 발급분 INSERT는 한 트랜잭션이다. 두 애그리거트 쓰기지만 "한 트랜잭션 하나의 애그리거트"의 좁은 예외로 둔다 — 한도 불변식은 선점과 발급 기록이 한 커밋으로 묶여야 보상 없이 성립하고(발급 실패 롤백이 선점을 자동 반환), 같은 도메인 모듈·스키마 내부라 MSA 분리와 충돌하지 않는다. 트랜잭션 분리(선점 후 별도 발급) 대안은 크래시 시 슬롯 누수·보상 경로가 생겨 기각했다.
  - 무효화·발급 실패는 카운트를 되돌리지 않는다(커밋된 발급 총량 기준 한도 — 슬롯 반환 없음).
- 발급 후 정책 변경 무영향: `use`는 `Coupon.status`를 재검사하지 않는다. 이미 발급된 쿠폰은 정책이 나중에 DISABLED가 돼도 계속 사용할 수 있다(발급분 자격만 검증). DISABLED는 신규 발급만 막고, 기발급분 사용 차단은 발급분 단위 무효화(revoke)가 담당한다.
- 무효화(revoke): 관리자가 미사용(ISSUED) 발급분을 사유와 함께 `ISSUED → REVOKED`로 전이하고 `revokedAt`·`revokeReason`을 세팅한다. REVOKED는 `use`가 거부하고 재전이가 없다. 사용된(USED) 발급분은 무효화를 거부한다 — 소급 회수·주문 금액 재계산은 범위 밖이다. 무효화와 동시 사용의 경합은 발급분 낙관락이 직렬화한다.
- 할인 계산: `calculateDiscount`는 `discount.applyTo(주문금액)`에 위임한다(정액/정률 규칙·상한·주문금액 clamp은 Discount 값 객체가 소유).
- 적용 조건: (할인 전) 주문금액 ≥ minOrderAmount 이고 발급분이 ISSUED, 본인(memberId) 소유, 사용 기한 내(`now ≤ expiresAt`)이며, 산출 할인이 0보다 클 때만 적용한다(0 할인이면 1회 발급을 헛되이 소진하지 않도록 적용을 거부). 발급분이 주어진 주문금액에 적용 가능한지와 예상 할인액의 미리보기(`getDiscountPreview`)도 같은 조건으로 판정하는 읽기 전용 조회다(상태 무변경).
- 소유: 본인(memberId) 소유가 아닌 발급분은 존재 누출 방지로 미존재로 취급한다.
- 사용(use): 주문 생성 이후 `use(issuedCouponId, memberId, orderId)`로 USED 전이하고 usedAt·orderId 세팅. 소유는 도메인이 `findByIdAndMemberId`로 강제해 본인 발급분이 아니면 미존재로 거부한다. 동시 두 주문이 같은 쿠폰을 사용하면 낙관락으로 한쪽만 성공하고 다른 쪽은 충돌로 보상된다(직렬화 메커니즘은 `docs/entity-persistence.md` 소유).
- 만료: 발급분 사용 기한(`expiresAt`) 경과분은 사용 시점 검증에서 거부한다(별도 만료 상태·배치 없음). 정책 `validUntil`은 발급 가능 기간의 종료이지 사용 만료가 아니며, 정책 변경이 기발급분 사용 기한을 소급 바꾸지 않는다(`expiresAt` 스냅샷).
- 취소 복원(restoreUse): 주문 취소·결제 실패로 주문이 무효가 되면 `restoreUse(issuedCouponId, orderId)`로 USED → ISSUED로 복원하고 usedAt·orderId를 clear한다. 가드 전이라 USED가 아니거나 사용 주문(orderId)이 일치하지 않으면 no-op이다(중복 보상 무해, 다른 주문에 재사용된 발급분을 풀지 않는다).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | name, discount, minOrderAmount, validity, usageValidDays, maxIssuance? | 값 객체 불변식(Discount·ValidityPeriod), usageValidDays ≥ 1, maxIssuance 있으면 ≥ 1, 최초 ACTIVE | 불변식 위반 |
| disable | couponId | ACTIVE→DISABLED | 미존재; 잘못된 전이 |
| enable | couponId | DISABLED→ACTIVE | 미존재; 잘못된 전이 |
| issue | couponId, memberId | ACTIVE, 발급기간 내, 회원 자격 활성, `(couponId, memberId)` 유니크, 한도 미소진(원자 선점), 최초 ISSUED | 쿠폰 미존재; 발급 중지(DISABLED); 발급기간 밖; 회원 자격 비활성(정지·탈퇴); 중복 발급; 한도 소진 |
| calculateDiscount | issuedCouponId, orderAmount | `discount.applyTo(orderAmount)`에 위임 | 미존재 |
| use | issuedCouponId, memberId, orderId | ISSUED·본인·사용 기한(`expiresAt`)·산출 할인 > 0 일 때만 USED 전이(본인·상태·기한은 도메인 강제, 산출 할인 > 0은 체크아웃 검증 계층 소유) | 미존재(미소유 포함); 상태·자격 위반; 만료; 낙관락 충돌 |
| restoreUse | issuedCouponId, orderId | 가드 USED ∧ 사용 주문 일치 → ISSUED(아니면 no-op), usedAt·orderId clear | — |
| revoke | issuedCouponId, reason | 가드 ISSUED→REVOKED, revokedAt·revokeReason 세팅 | 미존재; 사용됨(USED)·이미 무효화(REVOKED); 낙관락 충돌 |
| getIssuedCoupon | issuedCouponId | 본인 발급분 1행 | 미존재(미소유 포함) |
| getIssuableCoupons | pageable | ACTIVE ∧ 발급기간 내(validFrom≤now≤validUntil) ∧ 한도 미소진(maxIssuance null이거나 issuedCount<maxIssuance)인 정책만, 최신 등록순 페이지 | — |
| getIssuedCouponsByMember | memberId | 본인 발급분 전체, 최신순(생성시각 내림차순·동시각은 id 내림차순) 목록(없으면 빈 목록) | — |

- `getIssuableCoupons`·`getIssuedCouponsByMember`는 상태를 바꾸지 않는 읽기 전용 오퍼레이션이다.

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 6. 주문 (order)

주문과 주문 라인을 소유한다. 라인은 주문 시점의 변형·상품명·옵션·단가를, 배송지는 주문 시점 값을 스냅샷으로 보관한다(상품·변형·회원 변경과 무관하게 내역을 보존). 결제 축(`OrderStatus`)·이행 축(`FulfillmentStatus`)에 더해, 라인 단위 취소·반품 축(`OrderLineStatus`)과 주문 단위 반품 요청 축(`ReturnStatus`)을 갖는다. 정본 애그리거트 예시다.

- 애그리거트 루트: `Order`, 자식: `OrderLine`
- 스키마/테이블: `ordering` / `orders`, `order_line` (예약어 회피 divergence — 용어집 참조)

### Order 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| orderNumber | String | 필수 | 사람이 읽는 주문번호(유니크). `place` 시 생성. UUIDv7 PK와 분리된 고객·CS·정산 참조용 업무 식별자 |
| memberId | UUID | 필수 | 주문 회원 참조 |
| status | OrderStatus | 필수 | 아래 상태표(결제 축) |
| fulfillmentStatus | FulfillmentStatus | 필수 | 아래 이행 상태표(물리 이행 축). 최초 NOT_STARTED |
| totalAmount | Money | 필수 | 라인 합계(할인 전) |
| discountAmount | Money | 필수 | 쿠폰 할인액. 0 가능. 서버가 쿠폰 정책으로 계산 |
| shippingFee | Money | 필수 | 배송비(0 가능). 체크아웃 입력 스냅샷 |
| payAmount | Money | 필수 | 결제 대상 금액 = totalAmount − discountAmount + shippingFee (≥ 0) |
| refundedAmount | Money | 필수 | 부분 취소·부분 반품으로 환불된 누계. 초기 0. 라인 취소·반품 개시(`beginLineCancellation`/`beginLineReturn`)마다 확정 환불액을 가산해 루트 버전을 올린다(동시 라인 처리 직렬화). 완결(`completeLineCancellation`/`completeLineReturn`)은 이 필드를 건드리지 않되, 마지막 라인을 종결해 주문을 수렴시킬 때는 상태 필드 변경으로 역시 루트 버전을 올린다 |
| issuedCouponId | UUID | 선택 | 적용된 발급 쿠폰 참조 |
| shippingAddress | Address(VO) | 필수 | 배송지 스냅샷(다중 컬럼 `@Embeddable`) |
| shippedAt | Instant | 선택 | 출고 시각(SHIPPED 전 null) |
| carrier | String | 선택 | 택배사(`ship` 세팅, SHIPPED 전 null). 자유 문자열 — 택배사 카탈로그(enum)·API 연동은 범위 밖 |
| trackingNumber | String | 선택 | 운송장 번호(`ship` 세팅, SHIPPED 전 null) |
| deliveredAt | Instant | 선택 | 배송 완료 시각(DELIVERED 전 null) |
| stockDeductedAt | Instant | 선택 | 전 라인 재고 차감 완료 증거(`markStockDeducted` 세팅). 스윕·리컨실 보상의 재고 복원 게이트 |
| paidAt | Instant | 선택 | 결제 완료 시각(`markPaid` 세팅) |
| cancelRequestedAt | Instant | 선택 | 취소 개시 마커(`requestCancellation` 세팅, 재개시 no-op). 마커가 있는 동안 `ship`을 거부한다 |
| cancelledAt | Instant | 선택 | 취소 시각(`cancel` 세팅) |
| cancellationReason | CancellationReason | 선택 | 취소 사유(`cancel` 세팅) |
| refundedAt | Instant | 선택 | 반품 환불 시각(`refund` 세팅). REFUNDED에서만 존재 |
| refundReason | RefundReason | 선택 | 반품 환불 사유(`refund` 세팅, `CHANGE_OF_MIND`·`PRODUCT_DEFECT`·`WRONG_DELIVERY`·`CS_MANUAL`). REFUNDED에서만 존재 |
| returnStatus | ReturnStatus | 선택 | 주문 단위 반품 요청 축(아래 상태표). 요청이 없으면 없음 |
| returnRequestedAt | Instant | 선택 | 반품 요청 시각(`requestReturn` 세팅) |
| returnReason | RefundReason | 선택 | 반품 요청 사유(`requestReturn` 세팅). 승인되면 이 사유로 `refund`가 실행된다 |
| holdReason | HoldReason | 선택 | 이행 보류 사유. ON_HOLD에서만 존재(`holdFulfillment` 세팅, `releaseFulfillment` clear) |
| lines | Set\<OrderLine\> | 필수 | 1개 이상 |
| version | long | 필수 | 낙관락 버전(동시 취소·환불 전이·라인 환불 누계 가산 직렬화) |
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
| status | OrderLineStatus | 필수 | 라인 취소·반품 축 상태(아래 상태표). 최초 ORDERED |
| refundAmount | Money | 선택 | 확정된 라인 환불액. CANCELLING·RETURNING 진입 이후에만 존재 |
| returnReason | RefundReason | 선택 | 라인 반품 사유. RETURN_REQUESTED 진입 이후에만 존재(거절되면 clear) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태 (OrderStatus)

| 상태 | 의미 |
|---|---|
| PENDING | 주문 생성됨. 결제 진행 중 |
| PAID | 결제 완료 |
| CANCELLED | 취소됨(이행 완료 전 중단) |
| REFUNDED | 반품 환불됨(배송 완료 후 반품). 종료 상태 |

- 전이: `PENDING → PAID`(결제 완료), `PENDING → CANCELLED`(결제 실패·쿠폰 확정 실패 보상), `PAID → CANCELLED`(결제 후 사용자 취소·환불), `PAID → REFUNDED`(배송 완료 후 반품 환불, 이행 DELIVERED에서만). 발행 이벤트: PAID 전이 시 `OrderPaid`(아웃박스 기록).
- 라인 수렴 경로: 위 4개 전이가 `OrderStatus` 값을 바꾸는 유일한 간선이되, 진입 경로는 전체 단위 오퍼레이션 외에 라인 단위 수렴도 있다 — `completeLineCancellation`이 마지막 라인 취소를 완결하면 `PAID → CANCELLED`로(사유는 항상 `CUSTOMER_REQUEST`), `completeLineReturn`이 전 라인 종결(취소+반품 혼합 포함)을 완결하면 `PAID → REFUNDED`로 수렴한다.
- CANCELLED와 REFUNDED를 한 상태로 겹치지 않는다 — 취소는 이행 전 중단이라 이행 축이 PREPARING·ON_HOLD에서 동결되고, 반품 환불은 이행 완료 후 역전이라 이행 축이 DELIVERED로 남는다. CANCELLED 재사용(사유 구분)은 `cancel`의 출고 이후 거부 가드를 사유 분기로 우회해야 하고 "취소 주문 이행 동결" 불변식과 이행 DELIVERED 조합이 모순되어 기각했다. 불변식 `status == REFUNDED ⇔ refundedAt ≠ null ∧ refundReason ≠ null`.

### 상태 (OrderLineStatus)

라인 단위 취소·반품 축이다. 취소·반품은 라인 존재를 없애지 않고 상태만 바꾼다(라인 구성 불변 원칙과 정합).

| 상태 | 의미 |
|---|---|
| ORDERED | 주문됨 — 초기 상태 |
| CANCELLING | 취소 진행 중 — 환불액 확정됨, 결제 부분 환불 대기 |
| CANCELLED | 취소됨 — 종료 상태 |
| RETURN_REQUESTED | 반품 요청됨 — 관리자 승인·거절 대기 |
| RETURNING | 반품 진행 중 — 환불액 확정됨, 결제 부분 환불 대기 |
| RETURNED | 반품됨 — 종료 상태 |

- 전이: `ORDERED → CANCELLING`(`beginLineCancellation`) `→ CANCELLED`(`completeLineCancellation`). `ORDERED → RETURN_REQUESTED`(`requestLineReturn`) `→ ORDERED`(`rejectLineReturn`, 재요청 가능) 또는 `RETURN_REQUESTED → RETURNING`(`beginLineReturn`) `→ RETURNED`(`completeLineReturn`).

### 상태 (ReturnStatus)

주문 단위 반품 요청 축이다.

| 상태 | 의미 |
|---|---|
| REQUESTED | 반품 요청됨. PAID ∧ DELIVERED 주문에서 `requestReturn`으로 진입 |
| REJECTED | 반품 거절. REQUESTED에서 `rejectReturn`(관리자)으로 진입. 같은 주문에 재요청(`requestReturn`) 가능 |
| COMPLETED | 반품 완료. REQUESTED 주문이 `refund`로 REFUNDED에 도달하면(관리자 단일 액션 경로·승인 경로 무관) 부수적으로 진입하는 종료 상태 |

- 전이: 없음 → REQUESTED(`requestReturn`), REQUESTED → REJECTED(`rejectReturn`), REJECTED → REQUESTED(재요청), REQUESTED → COMPLETED(`refund`가 주문을 REFUNDED로 전이시킬 때 returnStatus가 REQUESTED였으면 부수 전이). `returnStatus`는 COMPLETED 전용 전이 메서드가 따로 있는 게 아니라 `refund()` 내부에서 조건부로 함께 세팅된다.

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
- 결제·이행 두 축은 저장상 직교하되 전이상 독립이 아니다: 모든 이행 전진은 `status == PAID`에서만 유효하고, 취소·환불·미결제 주문의 `fulfillmentStatus`는 동결된다(취소 주문 출고 불가, 환불 주문은 DELIVERED로 동결). 취소 진행 중 — 취소 개시 마커가 있거나 취소 진행 중(`CANCELLING`) 라인이 하나라도 있는 — 주문의 `ship`도 거부된다. `ON_HOLD ⇔ holdReason != null`(`FRAUD_REVIEW`·`ADDRESS_VERIFICATION`·`STOCK_DELAY`).

### 정책·불변식

- 생성(place): 라인 1개 이상. 회원은 자격 활성(`status = ACTIVE`·미탈퇴). 배송지 필수. `orderNumber` 생성. 최초 status PENDING, fulfillmentStatus NOT_STARTED, 각 라인 ORDERED, refundedAmount 0.
- 각 라인은 주문 시점 변형·상품명·옵션 표시·단가(variantId·productId·productName·optionLabel·unitPrice)를, 배송지는 주문 시점 값을 복사한다(스냅샷). 자기완결 스냅샷이라 이후 상품·변형·회원 변경이 주문 내역을 바꾸지 않고, 이력 조회가 변형·상품으로 fan-out하지 않는다. variantId는 재고 복원·SKU 식별 키다.
- 금액 불변식: `Order.place`가 자기 라인에서 totalAmount = Σ(라인 unitPrice × quantity)를 계산한다. discountAmount는 서버가 쿠폰 정책으로 산출해 입력하되(클라이언트 전달값 불신), Order 루트가 `discountAmount ≤ totalAmount`와 `issuedCouponId != null ⟺ discountAmount > 0`(쿠폰 없는 유령 할인·쿠폰 있는 0원 할인 동시 배제)을 자기 강제한다(위반 시 도메인 예외). payAmount = totalAmount − discountAmount + shippingFee ≥ 0.
- price ≥ 1·quantity ≥ 1이라 totalAmount ≥ 1이 항상 성립한다(무료·0원 주문 없음). payAmount = 0은 전액 할인 ∧ shippingFee = 0일 때만이고(배송비가 있으면 payAmount > 0), 그 경우 결제는 PG를 생략하고 자동 승인한다.
- 결제 완료(markPaid): PENDING에서만 PAID로. 함께 fulfillmentStatus NOT_STARTED→PREPARING, `paidAt` 세팅. 전이와 같은 트랜잭션에서 `OrderPaid`를 아웃박스 행으로 발행한다.
- 차감 완료 마커(markStockDeducted): PENDING에서만 `stockDeductedAt`을 세팅한다. 체크아웃이 전 라인 재고 차감 뒤에만 기록하므로 마커 존재는 전 라인 차감 완료를 보장한다 — 증거는 차감 완료 후에만 성립하고, 마지막 차감 커밋과 마커 커밋 사이 중단은 증거 없음으로 남는다(과소복원 방향). 스윕·리컨실 보상의 재고 복원이 이 증거를 게이트로 삼는다(크로스 도메인 정책 참조).
- 취소(cancel): PENDING 또는 PAID에서 CANCELLED로. PAID 취소는 `fulfillmentStatus ∈ {PREPARING, ON_HOLD}`일 때만 허용하고 SHIPPED·DELIVERED면 거부한다(배송 완료 후 대체 경로는 반품 환불 `refund`). PAID면 이행이 이미 최소 PREPARING이라 NOT_STARTED는 나타나지 않는다. 전이 시 `cancelledAt`·`cancellationReason`(`PAYMENT_FAILED`·`STOCK_SHORTAGE`·`COUPON_CONFLICT`·`CUSTOMER_REQUEST`·`ADMIN_ACTION`·`CHECKOUT_ABANDONED`)을 세팅한다. 전이는 1회만 유효하고, 후속 보상은 전이가 실제 일어난 호출에서만 태운다(중복 취소 무해). 동시 중복 취소가 둘 다 가드를 통과해도 낙관락(`@Version`)이 한쪽만 커밋시킨다(충돌 응답·재시도는 `docs/entity-persistence.md` 소유). 보상 내용은 소스 상태에 따른다 — PENDING이면 재고·쿠폰 복원, PAID이면 환불 선행 후 재고·쿠폰 복원(크로스 도메인 정책 참조).
- 취소 개시(requestCancellation): PAID ∧ 이행 PREPARING·ON_HOLD에서만 `cancelRequestedAt`을 세팅하고, 이미 개시된 주문엔 no-op이다(재시도 관용 — 복원을 게이트하는 1회성 전이가 아니라 출고 차단 마커라 멱등이 안전하다). 부분 취소·부분 반품 이력이 있으면(`refundedAmount != 0`) 거부한다(아래 부분 취소 이력 가드). 취소 파사드가 PG 환불 앞에 커밋해 취소 진행 중 주문의 `ship`을 거부한다. 마커 커밋은 낙관락(`@Version`)으로 `ship`과 직렬화된다 — `ship`이 먼저 커밋되면 개시가 가드·충돌로 거부돼 취소가 환불 전에 중단되고, 개시가 먼저 커밋되면 `ship`이 거부된다(경합 정합은 크로스 도메인 정책 참조). 마커는 해제 경로가 없고 취소 완결(CANCELLED) 후에도 남는다 — CANCELLED가 이행 전진을 이미 동결하므로 해제가 무의미하고, 취소 개시 시각 기록으로 남는다.
- 라인 취소 개시(beginLineCancellation): `status == PAID` ∧ 출고 전(이행이 SHIPPED·DELIVERED가 아님) ∧ 전체 취소 미개시(`cancelRequestedAt == null`) ∧ 대상 라인이 ORDERED일 때만 허용한다. 환불액을 계산해(`computeLineRefund`) 라인에 확정하고(ORDERED→CANCELLING) `refundedAmount`에 가산한 뒤 확정 환불액을 반환한다.
- 라인 환불액 계산(computeLineRefund, 취소·반품 공유): 아직 환불액이 확정되지 않은 라인(`refundAmount == null`)이 이 라인 하나만 남았으면(마지막) `payAmount − refundedAmount`(결제 잔액 전액 — 끝전·배송비 흡수), 아니면 `lineAmount − discountShare(line)`(라인 금액에서 안분 할인 차감)이다.
- 쿠폰 할인 비례 안분(discountShare): `discountAmount × lineAmount / totalAmount`를 정수 나눗셈(내림)으로 계산한다. 내림으로 생기는 오차(끝전)는 항상 마지막 라인의 전액 환불이 흡수해 안분 합계가 총할인과 정확히 일치한다.
- 라인 취소 완결(completeLineCancellation): 대상 라인이 CANCELLING이어야 한다. CANCELLED로 완결한 뒤, 전 라인이 CANCELLED면 주문을 CANCELLED로 수렴시킨다(`cancellationReason`은 항상 `CUSTOMER_REQUEST`로 고정).
- 부분 취소 이력 가드: `refundedAmount != 0`이면 `requestCancellation`·`refund`·`requestReturn`(전체 단위 전이 개시 3종)을 거부한다. 부분 집행 후 전체 집행이 겹치는 이중 환불·이중 재고 복원을 막는다 — 남은 라인은 라인 단위로만 처리한다. 반면 `cancel()`(전이 자체)과 `requestLineReturn`(라인 단위 조작)은 이 가드를 자체적으로 갖지 않는다 — 전자는 호출 경로(체크아웃 보상·취소 파사드·스윕·리컨실이 PENDING이거나 `requestCancellation` 선행)가, 후자는 라인 단위라 전체 재집행 위험이 없음이 안전을 보장한다.
- 전체 반품 요청(requestReturn): 이미 REQUESTED면 거부. 부분 취소·부분 반품 이력(`refundedAmount != 0`)이 있으면 거부. 라인 중 하나라도 RETURN_REQUESTED·RETURNING이면 거부(라인 반품 진행과 전체 요청 상호 배타). PAID ∧ DELIVERED가 아니면 거부. 통과 시 `returnStatus = REQUESTED`·`returnRequestedAt`·`returnReason`을 세팅한다.
- 전체 반품 거절(rejectReturn): returnStatus가 REQUESTED가 아니면 거부. REQUESTED→REJECTED만 세팅하고 주문의 결제·이행 축은 변경하지 않는다(주문은 PAID·DELIVERED로 남는다, 재요청 가능).
- 라인 반품 요청(requestLineReturn): PAID ∧ DELIVERED가 아니면 거부. 전체 반품이 REQUESTED 중이면 거부. 대상 라인이 ORDERED가 아니면 거부. 라인을 RETURN_REQUESTED로 전이하고 `returnReason`을 라인에 기록한다.
- 라인 반품 거절(rejectLineReturn): 라인이 RETURN_REQUESTED가 아니면 거부. 라인을 ORDERED로 되돌리고 라인 `returnReason`을 지운다(재요청 가능).
- 라인 반품 승인 개시(beginLineReturn): PAID ∧ DELIVERED가 아니면 거부. 전체 반품이 REQUESTED 중이면 거부. 라인이 RETURN_REQUESTED가 아니면 거부. 환불액을 계산해(취소와 같은 `computeLineRefund` 산식) 라인에 확정하고(RETURN_REQUESTED→RETURNING) `refundedAmount`에 가산한 뒤 확정 환불액을 반환한다.
- 라인 반품 완결(completeLineReturn): 라인이 RETURNING이 아니면 거부. RETURNED로 완결한다. 전 라인이 CANCELLED 또는 RETURNED로 종결되면(취소·반품 혼합 종결 포함) 주문을 REFUNDED로 수렴시키고 `refundReason`을 그 완결을 트리거한 라인의 returnReason으로 세팅한다.
- 반품 환불(refund): PAID ∧ 이행 DELIVERED에서만 REFUNDED로. REFUNDED에서 재실행 거부. 부분 취소·부분 반품 이력(`refundedAmount != 0`)이 있으면 거부. 라인 중 하나라도 ORDERED가 아니면(반품·취소 진행/완료 라인이 있으면) 거부한다 — 라인 워크플로와의 이중 집행 방지. 전이 시 `refundedAt`·`refundReason`을 세팅하고 이행 축은 DELIVERED로 남는다. `returnStatus`가 REQUESTED였으면(승인 경로로 호출된 경우) COMPLETED로 부수 전이한다. 취소·환불 상호 재전이가 없다(REFUNDED 주문 취소 불가, CANCELLED 주문 환불 불가). 동시 중복 환불도 취소와 같이 낙관락(`@Version`)이 한쪽만 커밋시킨다. SHIPPED는 취소도 환불도 안 되는 의도된 공백이다(배송 완료 확정 후 환불). 반품 승인(approveReturn)은 별도 도메인 전이가 아니라 앱 계층 파사드가 주문의 `returnReason`을 읽어 그대로 `refund()`를 재사용하는 조립이다 — 관리자 단일 액션 경로와 승인 경로가 도메인 계층에서는 하나의 전이(`refund`)를 공유한다(크로스 도메인 정책 참조). 후속 보상(환불·복원)은 크로스 도메인 정책이 소유한다.
- 이행(ship·confirmDelivery·holdFulfillment·releaseFulfillment): 모든 이행 전진은 `status == PAID`에서만 유효하다(취소·미결제 주문은 fulfillment 동결). `ship`은 PREPARING→SHIPPED(+`shippedAt`, 필수 입력 `carrier`·`trackingNumber` 기록)이되 취소 개시 마커(`cancelRequestedAt`)가 있거나 취소 진행 중(`CANCELLING`) 라인이 하나라도 있으면 거부한다(취소 진행 중 출고 차단 — 전체·라인 공통), `confirmDelivery`는 SHIPPED→DELIVERED(+`deliveredAt`), `holdFulfillment`는 PREPARING→ON_HOLD(+`holdReason`: 사기 심사·배송지 확인·입고 지연), `releaseFulfillment`는 ON_HOLD→PREPARING(`holdReason` clear). 분할 배송·부분 이행은 범위 밖(단일 배송·단일 이행). 배송 완료 후 반품 환불은 이행 축이 아니라 결제 축 `refund`(→REFUNDED)가 소유한다(이행 축은 DELIVERED로 남는다).
- 생성 후 라인 구성은 불변(수정 불가). 라인 취소·반품은 라인을 제거하지 않고 상태만 바꾼다. 구성 변경이 필요하면 취소 후 재주문.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| place | memberId, 라인 스냅샷, shippingAddress, discountAmount, shippingFee, issuedCouponId? | 라인 ≥ 1, `orderNumber` 생성, totalAmount 자기 계산, discountAmount ≤ totalAmount, `issuedCouponId ⟺ discount>0`, payAmount = total−discount+shippingFee, 최초 PENDING·NOT_STARTED·라인 ORDERED | 빈 주문(라인 0); 회원 탈퇴 또는 정지; 할인 초과 |
| markPaid | orderId | PENDING→PAID, 이행 NOT_STARTED→PREPARING, `paidAt` 세팅, `OrderPaid` 아웃박스 발행(같은 트랜잭션) | 미존재; 잘못된 전이 |
| markStockDeducted | orderId | PENDING에서만 `stockDeductedAt` 세팅(전 라인 재고 차감 완료 증거) | 미존재; 잘못된 전이(비PENDING); 낙관락 충돌 |
| cancel | orderId, reason | PENDING·PAID → CANCELLED 1회(PAID는 이행 PREPARING·ON_HOLD만), `cancelledAt`·`cancellationReason` 세팅, 보상은 소스 상태 함수 | 미존재; 잘못된 전이(REFUNDED 포함); 출고 이후(SHIPPED·DELIVERED); 낙관락 충돌 |
| requestCancellation | orderId | PAID ∧ 이행 PREPARING·ON_HOLD ∧ 부분 취소 이력 없음에서 `cancelRequestedAt` 세팅, 재개시 no-op | 미존재; 결제 축 PAID 아님; 출고 이후(SHIPPED·DELIVERED); 부분 취소 이력 있음; 낙관락 충돌 |
| beginLineCancellation | orderId, lineId | PAID ∧ 출고 전 ∧ `cancelRequestedAt == null` ∧ 라인 ORDERED에서 환불액 확정(ORDERED→CANCELLING)·`refundedAmount` 가산, 확정 환불액 반환 | 미존재(주문·라인); 출고 이후; 전체 취소 개시됨; 라인이 ORDERED 아님; 낙관락 충돌 |
| completeLineCancellation | orderId, lineId | CANCELLING 라인만 CANCELLED로 완결, 전 라인 CANCELLED면 주문 CANCELLED 수렴(사유 CUSTOMER_REQUEST) | 미존재(주문·라인); 라인이 CANCELLING 아님; 낙관락 충돌 |
| requestReturn | orderId, memberId, reason | PAID ∧ DELIVERED에서 returnStatus=REQUESTED·`returnRequestedAt`·`returnReason` 세팅 | 미존재(본인 아님 포함 — 존재 누출 방지); 이미 요청됨(전체·라인); 부분 취소 이력 있음; 배송 완료 아님 |
| rejectReturn | orderId | REQUESTED→REJECTED(결제·이행 축 무변경, 재요청 가능) | 미존재; 반품 요청 없음 |
| requestLineReturn | orderId, memberId, lineId, reason | 라인 ORDERED→RETURN_REQUESTED·라인 `returnReason` 세팅 | 미존재(본인 아님·라인 미존재 포함); 전체 반품 진행 중; 배송 완료 아님; 라인이 ORDERED 아님 |
| rejectLineReturn | orderId, lineId | 라인 RETURN_REQUESTED→ORDERED, 라인 `returnReason` clear(재요청 가능) | 미존재(주문·라인); 반품 요청 없음 |
| beginLineReturn | orderId, lineId | 라인 RETURN_REQUESTED→RETURNING, 환불액 확정·`refundedAmount` 가산, 확정 환불액 반환 | 미존재(주문·라인); 전체 반품 진행 중; 배송 완료 아님; 반품 요청 없음; 낙관락 충돌 |
| completeLineReturn | orderId, lineId | 라인 RETURNING→RETURNED, 전 라인 종결(취소+반품 혼합 포함) 시 주문 REFUNDED 수렴(`refundReason` = 트리거 라인의 returnReason) | 미존재(주문·라인); 라인이 RETURNING 아님; 낙관락 충돌 |
| refund | orderId, reason | PAID ∧ 이행 DELIVERED ∧ 부분 취소 이력 없음 ∧ 전 라인 ORDERED → REFUNDED 1회, `refundedAt`·`refundReason` 세팅, returnStatus REQUESTED였으면 COMPLETED 부수 전이, 이행 축 DELIVERED 유지 | 미존재; 잘못된 전이(이미 REFUNDED); 배송 완료 전; 부분 취소 이력 있음; ORDERED 아닌 라인 존재; 낙관락 충돌 |
| ship | orderId, carrier, trackingNumber | `status == PAID` ∧ PREPARING→SHIPPED ∧ 취소 개시 마커 없음 ∧ CANCELLING 라인 없음, `shippedAt`·`carrier`·`trackingNumber` 세팅 | 미존재; 결제 축 PAID 아님(취소·미결제); 잘못된 전이; 취소 진행 중(마커·라인) |
| confirmDelivery | orderId | `status == PAID` ∧ SHIPPED→DELIVERED, `deliveredAt` 세팅 | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| holdFulfillment | orderId, reason | `status == PAID` ∧ PREPARING→ON_HOLD, `holdReason` 세팅 | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| releaseFulfillment | orderId | `status == PAID` ∧ ON_HOLD→PREPARING, `holdReason` clear | 미존재; 결제 축 PAID 아님; 잘못된 전이 |
| getOrder | orderId | 주문 1행 | 미존재 |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 7. 결제 (payment)

주문 결제를 소유한다. 외부 PG 연동은 도메인이 소유한 벤더 중립 포트(`PaymentGateway`)로 하고, 실제 구현은 외부 어댑터가 담당한다.

- 애그리거트 루트: `Payment`
- 스키마/테이블: `payment` / `payment`
- 포트: `PaymentGateway`(approve·cancel·cancelPartially[부분 환불]·inquire[거래 상태 조회]). 구현은 외부 어댑터(실패·응답 유실을 트리거 금액으로 시뮬레이션하는 연습용 fake). approve가 가맹점 참조(결제 ID)를 실어 inquire의 조회 키가 된다 — 응답 유실 시 호출자는 PG 거래 ID를 모른다.

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| orderId | UUID | 필수 | 주문 참조(유니크 — 주문당 결제 1행) |
| amount | Money | 필수 | 결제 금액 = 주문 payAmount |
| refundedAmount | Money | 필수 | 부분 취소로 환불된 누계. 0에서 시작하고 단조 증가하며 결제 금액(`amount`)을 초과할 수 없다. 누계가 `amount`에 도달하면 CANCELLED로 전이한다 |
| status | PaymentStatus | 필수 | 아래 상태표 |
| method | PaymentMethod | 선택 | 결제 수단(동기 승인 수단: CARD·EASY_PAY·BANK_TRANSFER). `amount > 0`일 때만 존재(불변식) |
| failureReason | FailureReason | 선택 | FAILED 사유. approve 실패 시 세팅 |
| pgTransactionId | String | 선택 | PG 승인 거래 ID(승인 후 채워짐) |
| pgCancelTransactionId | String | 선택 | PG 취소(환불) 거래 ID. 전액 취소(`cancel`)가 PG 환불 호출 시 세팅. 부분 환불 경로는 세팅하지 않는다 |
| approvedAt | Instant | 선택 | 승인 시각(approve 성공 시 세팅) |
| cancelledAt | Instant | 선택 | 취소·환불 시각. `cancel` 세팅, 부분 환불 누계가 결제액에 도달할 때(`syncPartialRefund`)도 세팅 |
| version | long | 필수 | 낙관락 버전(동시 취소 전이 직렬화) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 낙관락(`@Version`)을 둔다. 주문당 결제 1행(orderId 유니크)이어도 취소(환불) 전이에는 사용자 취소·리컨실·웹훅 확정이 겹칠 수 있어, 동시 중복 전이가 둘 다 가드를 통과해도 한쪽만 커밋된다(이중 환불·이중 복원 차단의 결제측 축). 재요청은 상태 가드로 막는다.

### 상태 (PaymentStatus)

| 상태 | 의미 |
|---|---|
| REQUESTED | 결제 요청됨 |
| APPROVED | 승인 완료 |
| FAILED | 승인 실패 |
| CANCELLED | 취소·환불됨 |

- 전이: `REQUESTED → APPROVED`, `REQUESTED → FAILED`, `APPROVED → CANCELLED`(환불). 부분 환불 누계가 결제액에 도달해 종결되는 경로도 같은 `APPROVED → CANCELLED` 간선이다(상태 그래프는 이 3개 전이로 완전).

### 정책·불변식

- 요청(request): 주문당 결제 1행(orderId 유니크). amount = 주문 payAmount. 최초 REQUESTED, refundedAmount 0.
- 승인: `PaymentGateway.approve` 성공 시 APPROVED + pgTransactionId, 실패 시 FAILED + `failureReason`(`INSUFFICIENT_BALANCE`·`LIMIT_EXCEEDED`·`INVALID_METHOD`·`RISK_DECLINED`·`GATEWAY_ERROR`). `payAmount == 0`(전액 할인)이면 PG를 호출하지 않고 APPROVED로 자동 처리한다(pgTransactionId 없음). 체크아웃 파사드가 동기 반환된 사유로 보상·안내를 분기한다.
- 취소·환불(cancel): APPROVED에서만 CANCELLED로. 부분 환불 이력(`refundedAmount ≠ 0`)이 있으면 APPROVED여도 거부한다 — 전액 PG 취소를 호출하면 이미 부분 환불된 금액이 이중 환불되므로, 남은 금액은 반드시 `cancelPartially`로 라인 단위로 마저 취소해야 한다. `PaymentGateway.cancel` 호출 시 취소 거래 ID를 `pgCancelTransactionId`에 저장한다(승인 ID `pgTransactionId`와 분리 — 정산·CS 대사). 단 `pgTransactionId == null`(PG 미호출 승인)이면 환불 호출을 생략하고 상태만 CANCELLED로 둔다. 이미 CANCELLED인 결제의 취소 재호출은 서비스 계층이 예외 없이 조용히 통과시킨다(재시도 관용).
- 부분 환불(cancelPartially): APPROVED에서만 유효하다. `PaymentGateway.cancelPartially` 호출(이번 환불분이 0이거나 `pgTransactionId == null`인 PG 미호출 승인이면 생략) 후, 환불 누계를 호출자가 전달한 누계 총액(`cumulativeTotal`)으로 단조 동기화한다 — 같거나 낮은 누계의 재동기화는 이중 반영 없이 통과한다(재시도·재개 멱등). 누계가 `amount`에 도달하면 CANCELLED로 전이하고 `cancelledAt`을 세팅한다. 누계가 `amount`를 초과하면 거부한다. 이미 CANCELLED인 결제는 조용히 통과한다. 개별 부분 환불의 PG 거래 ID는 영속하지 않는다.
- 멱등: 이미 결제된 주문(APPROVED)에 재요청은 상태 가드로 거부. 결제 실패로 주문이 취소되면 재체크아웃은 새 주문(새 orderId)을 만든다 — 실패 결제 행은 새 주문을 막지 않는다.
- 리컨실 확정(confirmApproval·confirmFailure·confirmOrphanedApproval): 응답 유실로 REQUESTED에 남은 결제를 PG 재청구 없이 상태 조회(inquire) 결과로만 기록한다. 승인 확정은 APPROVED + pgTransactionId, 거절 확정은 FAILED + 사유, 청구 미도달(NOT_FOUND)은 돈이 움직이지 않았으므로 FAILED(`GATEWAY_ERROR`)로 확정한다. 주문이 이미 취소·환불로 종결된 지연 승인(고아 청구)은 confirmOrphanedApproval이 PG 환불을 먼저 수행하고 승인·취소 기록을 한 커밋으로 남긴다 — 어느 지점에서 중단돼도 REQUESTED로 남아 재시도되고, 환불 재호출은 결정론적 멱등 키가 흡수한다. 확정 대상 선별·주문측 조율(결제완료·보상·고아 청구 환불)은 크로스 도메인 정책이 소유한다.
- 결제 수단: 승인/실패가 동기 확정되는 수단만(`CARD`·`EASY_PAY`·`BANK_TRANSFER`). 불변식 `amount > 0 ⇔ method != null` — `amount == 0`(전액 할인) 결제는 실 지불 수단이 없으므로 `method` 없이 PG 생략 자동 승인하고 가짜 `CARD`를 채우지 않는다. 가상계좌 등 입금 대기·비동기 통지 수단은 범위 밖(실 PG·웹훅 기준선 밖). 수단별 부가 데이터(할부·provider)는 단일 상세 컬럼으로 겸용하지 않는다.
- 도메인 이벤트 없음. 결제 결과는 동기 승인 PG가 호출자(체크아웃 파사드)에게 즉시 반환하므로, 승인/실패 반영은 파사드가 동기로 처리한다. 동기 경로가 종결하지 못한 잔여(응답 유실·기록 후 중단)만 리컨실 확정 경로가 닫는다(크로스 도메인 정책 참조).

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| request | orderId, amount, method? | orderId 유니크, amount = 주문 payAmount, `amount>0 ⇔ method≠null`, 최초 REQUESTED | 주문당 결제 중복 |
| approve | paymentId | REQUESTED→APPROVED(+pgTransactionId·`approvedAt`) 또는 FAILED(+failureReason); `payAmount == 0`이면 PG 생략 자동 승인 | 미존재; 잘못된 전이; 낙관락 충돌 |
| cancel | paymentId | APPROVED→CANCELLED(+`cancelledAt`); 부분 환불 이력 없음(`refundedAmount == 0`); PG 환불 시 `pgCancelTransactionId` 세팅; `pgTransactionId == null`이면 PG 생략; 이미 CANCELLED면 조용히 통과 | 미존재; 잘못된 전이; 부분 환불 이력 있음; 낙관락 충돌 |
| cancelPartially | paymentId, amount(이번 환불분), cumulativeTotal(환불 누계), refundKey | APPROVED에서 `refundedAmount`를 `cumulativeTotal`로 단조 동기화(+PG 부분 환불 호출, `amount==0`이거나 PG 미호출 승인이면 생략); 누계가 결제액에 도달하면 CANCELLED 전이(+`cancelledAt`); 이미 CANCELLED면 조용히 통과 | 미존재; 잘못된 전이(APPROVED 아님); 누계가 결제액 초과; 낙관락 충돌 |
| inquireGateway | paymentId | PG 거래 상태 조회(포트 위임). 결제 상태 무변경 | 미존재 |
| confirmApproval | paymentId, pgTransactionId | REQUESTED→APPROVED(+`approvedAt`). PG 재청구 없음 | 미존재; 잘못된 전이; 낙관락 충돌 |
| confirmFailure | paymentId, failureReason | REQUESTED→FAILED(+failureReason). PG 재청구 없음 | 미존재; 잘못된 전이; 낙관락 충돌 |
| confirmOrphanedApproval | paymentId, pgTransactionId | PG 환불 선행 후 REQUESTED→APPROVED→CANCELLED 한 커밋(+`approvedAt`·`cancelledAt`·`pgCancelTransactionId`) | 미존재; 잘못된 전이; 낙관락 충돌 |

포트 `PaymentGateway`는 도메인이 소유하고 구현은 외부 어댑터다. 반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 8. 위시리스트 (wishlist)

회원이 상품에 붙이는 찜과, 재입고 시 찜한 회원에게 보내는 알림 처리 기록을 소유한다. 두 엔티티는 서로 참조하지 않는 독립 루트다.

- 애그리거트 루트: `WishlistItem`, `RestockNotification`(각각 단일 엔티티, 자식 없음)
- 스키마/테이블: `wishlist` / `wishlist_item`, `restock_notification`

### WishlistItem 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 회원 논리 참조(FK 없음). (memberId, productId) 유니크 |
| productId | UUID | 필수 | 상품 논리 참조(FK 없음). (memberId, productId) 유니크 |
| createdAt | Instant | 필수 | 생성 시각(Auditing) — 조회 모델에 "찜한 시각"으로 노출 |
| updatedAt | Instant | 필수 | 수정 시각(Auditing) |

### RestockNotification 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| eventId | UUID | 필수 | 처리한 `StockRestocked` 이벤트 식별자. 유니크(멱등 가드) |
| createdAt | Instant | 필수 | 생성 시각(Auditing) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing) |

### 상태

상태 없음(두 엔티티 모두). 찜 삭제는 즉시 물리 삭제다(`deletedAt` 없음).

### 정책·불변식

- 담기 필수값: memberId, productId. 상품 존재·판매 상태(ON_SALE/HIDDEN·삭제)를 검사하지 않는다 — 장바구니 담기(자격 게이트)와 달리 찜에는 게이트가 없다.
- 담기는 행 자체가 멱등 단위다: (memberId, productId) 행이 이미 있으면 아무 것도 하지 않는다. 동시 중복 담기 경합은 담기 서비스가 독립 물리 트랜잭션(REQUIRES_NEW)으로 저장을 시도하고 유니크 위반을 no-op으로 흡수해 종결 상태(찜 존재)로 수렴한다 — 합산할 값이 없어 유실할 상태도 없으므로 장바구니 라인과 달리 낙관락(`@Version`)을 두지 않는다.
- 삭제는 즉시 물리 삭제다 — 소프트 삭제(`deletedAt`) 아님. 없는 행 삭제는 no-op(멱등).
- 소유권은 쿼리 스코프(memberId AND productId, memberId는 토큰 주체에서 도출)로 구조적으로 강제한다 — 별도 소유권 검사·거부 예외 없이 다른 회원의 행은 애초 조회·삭제 대상에 들지 않는다.
- 목록 정렬: UUIDv7 PK가 생성 시각 순서를 담아 id 내림차순이 최신 찜 우선 정렬을 겸한다(별도 정렬 컬럼 없음).
- 재입고 알림 자격: 별도 신청 절차가 없다 — 상품을 찜하는 것 자체가 그 상품의 재입고 알림 자격이다.
- `RestockNotification`은 (이벤트, 회원) 단위가 아니라 이벤트 단위 처리 기록이다 — 한 이벤트 처리 중 일부 회원 발송 도중 예외가 나면 처리 기록 저장(마지막 단계)에 이르지 못해 이벤트 전체가 미처리로 남고, 재전달 시 이미 발송받은 회원도 포함해 전원에게 재발송된다(회원별 발송 여부는 추적하지 않는다).
- 재입고 알림 소비(`StockRestockedListener`, `StockRestocked` 소비): (1) `RestockNotification.eventId` 존재 여부로 멱등 가드 — 처리 이력이 있으면 스킵. (2) 변형 → 상품(productId·상품명) 해석. (3) 그 상품을 찜한 회원 중 미탈퇴 회원(정지 회원 포함, 탈퇴 회원 제외)의 이메일로 상품명을 담은 재입고 메일 발송. (4) 처리 기록(`RestockNotification`) 저장. 발송은 취소 불가 부작용이라 트랜잭션 밖에서 수행하고 처리 기록만 영속한다 — 발송 후 기록 순서라 크래시 창은 재발송(at-least-once)이다.
- 발송 시점 조회(변형 → 상품·상품명·회원 이메일)는 재입고 시점의 최신 상태를 그대로 쓴다 — 상품이 그 사이 HIDDEN·삭제로 전환됐는지는 재검사하지 않는다(상품 조회는 미삭제만 요구, 노출 여부 무관).
- 메일 발송 포트: `MailGateway` — 제목·본문 조립은 구현이 소유한다. 현재 구현은 실제 발송 없이 로그로 남기는 개발용 스텁이다(`REQUIREMENTS.md` 외부 연동 참조).
- 이벤트 페이로드·발행 시점은 도메인 이벤트 명세 절이 소유한다.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| add | memberId, productId | (memberId, productId) 유일 행. 중복 담기는 no-op(존재 확인 후 저장, 유니크 위반은 흡수) | — (상품 미존재여도 거부하지 않는다) |
| remove | memberId, productId | (memberId, productId) 행 물리 삭제 | — (미존재 행이어도 거부하지 않는다) |
| getWishlist | memberId | 최신 찜 우선(id 내림차순) 목록 | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 9. 리뷰 (review)

회원이 구매확정(배송 완료)한 상품에 남기는 평가를 소유한다. 애그리거트 루트 단독(자식 없음), member·product 도메인은 UUID 논리 참조만 갖는다.

- 애그리거트 루트: `Review`
- 스키마/테이블: `review` / `review`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 작성 회원 참조(member 도메인 논리 참조) |
| productId | UUID | 필수 | 리뷰 대상 상품 참조(product 도메인 논리 참조). (memberId, productId) 활성 리뷰(`deletedAt IS NULL`) 한정 부분 유니크 |
| rating | int | 필수 | 별점 1~5 |
| content | String | 필수 | 본문 1~1000자(공백만인 본문 거부) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록). 목록 정렬의 "작성 시각"으로 노출 |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 제거(본인 또는 관리자) 시각. 제거된 리뷰만 값을 가짐 |
| removedReason | String | 선택 | 관리자 제거 사유. 관리자 제거된 리뷰만 값을 가짐(본인 삭제는 사유 없음) |
| deletedBy | DeletedBy | 선택 | 삭제 주체(MEMBER/ADMIN). 제거된 리뷰만 값을 가짐 |

- version(낙관락) 없음 — 동시 작성 경합은 유니크 인덱스 + 사전 exists 검사로 처리하고(아래 정책), 별점·본문 자체는 동시 수정 경합의 대상으로 보지 않는다.

### 상태

전용 상태 enum 없음. `deletedAt` 유무로 활성/제거 두 상태만 구분한다(활성 조회는 `deletedAt IS NULL`). 제거 상태의 주체는 `deletedBy`(MEMBER/ADMIN)로 구분한다.

### 정책·불변식

- 작성 자격(구매확정 게이트): review 도메인 밖, 앱 계층 파사드가 order 도메인을 조회해 강제한다 — 해당 회원이 해당 상품에 대해 결제 완료(PAID) ∧ 배송 완료(DELIVERED)인 주문 라인을 하나라도 가져야 한다. review 도메인 자체는 이 자격을 모른다(크로스 도메인 정책 참조).
- 반품 환불된 주문은 자격에서 제외된다 — 환불은 주문 상태를 PAID에서 REFUNDED로 전이시키므로(주문 도메인 정책), 배송 후 전액 환불된 구매 건만으로는 신규 리뷰를 쓸 수 없다. 환불 전에 이미 쓴 리뷰는 영향받지 않는다(리뷰는 근거 주문을 참조·저장하지 않는다).
- (memberId, productId) 활성 리뷰 한정 부분 유니크(`WHERE deleted_at IS NULL`) — 상품당 활성 리뷰 한 건. DB 유니크 인덱스가 최후 방어선이고, 애플리케이션은 저장 전 exists 검사로 사전 거부한다. 이 exists 검사는 활성 리뷰와 관리자 제거 리뷰를 포함한다 — 본인 삭제 리뷰는 포함하지 않는다(재작성 허용 정책과 정합).
- 동시 중복 작성 경합: 사전 exists 검사와 저장을 독립 트랜잭션(REQUIRES_NEW)에서 수행해, 유니크 위반이 외부(파사드) 트랜잭션을 rollback-only로 오염시키지 않게 격리한다. 저장 후 즉시 flush로 유니크 위반을 그 트랜잭션 안에서 표면화해 중복 작성 거부로 흡수한다.
- 관리자 제거와 본인 삭제는 둘 다 소프트삭제다: 관리자 제거는 `deletedAt`·`removedReason`·`deletedBy`(ADMIN)를 채우고, 본인 삭제는 `deletedAt`·`deletedBy`(MEMBER)만 채운다(사유 없음). 재작성 가능 여부는 `deletedBy`가 가른다 — 본인 삭제 행은 exists 검사에서 제외돼 같은 상품에 재작성할 수 있고, 관리자 제거 행은 exists 검사에 포함돼 재작성이 영구 차단된다. 부분 유니크 인덱스는 `deletedAt`만 보므로 활성 행 동시 중복만 최후 방어하고, 관리자 제거 행의 재작성 차단은 애플리케이션 exists 검사가 전담한다.
- 별점 1~5, 본문 1~1000자(공백만 거부) — 작성(`write`)·수정(`revise`) 모두 같은 검증.
- 수정·본인 삭제는 소유 검증을 리뷰 조회 조건에 접어 넣는다 — 존재하되 소유가 아니거나 이미 관리자 제거된 리뷰는 모두 동일하게 미존재로 거부한다(소유 여부·제거 여부를 구분해 노출하지 않음).
- 관리자 제거는 소유 무관하게 `reviewId`만으로 대상을 찾되 `deletedAt IS NULL`인 것만 대상이다 — 이미 제거된 리뷰의 재제거는 미존재로 거부한다.
- 목록 조회는 상품별로 `deletedAt IS NULL`인 리뷰만, ID 내림차순으로 정렬한다(UUIDv7 ID가 생성 시각 순서라 ID 내림차순이 최신 작성 우선 정렬을 겸함). 인증 불필요(공개 표면 분류는 `REQUIREMENTS.md` 소유).
- 평점 집계(상품 평균 별점·리뷰 수 등)는 없다 — product 도메인에 집계 필드·이벤트 연동이 없고, 개별 리뷰의 `rating`만 노출한다.
- 도메인 이벤트 없음 — review 도메인은 이벤트를 발행하지 않는다.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| write(작성 자격 게이트는 파사드 경유) | memberId, productId, rating, content | 배송완료(PAID·DELIVERED) 구매 이력 존재(파사드 사전 검증); (memberId, productId) 활성 또는 관리자 제거 이력 없음(사전 exists 검사 + REQUIRES_NEW 격리 + 부분 유니크 인덱스 최후 방어); rating 1~5; content 1~1000자(공백만 거부) | 배송완료 이력 없음(파사드 소유); 중복 작성; 별점·본문 범위 위반 |
| revise | reviewId, memberId, rating, content | 본인 소유 ∧ 미제거 리뷰만 대상; rating·content 같은 검증 | 미존재(타인 소유·이미 관리자 제거 포함); 범위 위반 |
| remove(본인 삭제) | reviewId, memberId | 본인 소유 ∧ 미제거 리뷰만 대상; 소프트삭제(`deletedAt`·`deletedBy`=MEMBER 기록, 사유 없음, 재작성 가능 유지) | 미존재(타인 소유·이미 관리자 제거 포함) |
| removeByAdmin | reviewId, reason | 미제거(deletedAt null) 리뷰만 대상; 소유 무관; 소프트삭제(`deletedAt`·`removedReason`·`deletedBy`=ADMIN 기록, 재작성 영구 차단) | 미존재(이미 제거 포함) |
| getProductPage | productId, pageable | `deletedAt IS NULL`인 리뷰만, ID 내림차순(=최신 작성 우선) 페이지 | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 10. 문의 (inquiry)

회원이 상품에 남긴 문의를 소유한다. 단일 엔티티(자식 없음).

- 애그리거트 루트: `Inquiry`
- 스키마/테이블: `inquiry` / `inquiry`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 작성 회원 참조(논리 참조, FK 없음) |
| productId | UUID | 필수 | 문의 대상 상품 참조(논리 참조, FK 없음, 존재 미검증) |
| content | String | 필수 | 문의 본문. 1~1000자(공백 불가) |
| secret | boolean | 필수 | 비밀글 여부 |
| answer | String | 선택 | 관리자 답변 본문. 1~1000자(공백 불가). 미답변이면 null |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 상태 (파생, 별도 컬럼 없음)

| 상태 | 판별 |
|---|---|
| 미답변 | `answer IS NULL` |
| 답변완료 | `answer IS NOT NULL` |

- 상태 컬럼을 따로 두지 않고 `answer`의 null 여부로 판별한다(답변 여부 필터 조회도 동일 기준).

### 정책·불변식

- 본문 검증: 작성·답변 공통으로 1~1000자, 공백만으로는 불가. 위반 시 거부.
- 답변은 최초 등록이든 재등록이든 동일 연산(`answer`)이 덮어쓴다 — 이전 답변 이력은 보존하지 않는다.
- 작성 자격: 인증된 회원이면 작성할 수 있다. 도메인 자체는 회원 자격 상태(정지·탈퇴) 검증이나 상품 존재 검증을 하지 않는다 — cart·coupon·order가 공통으로 요구하는 "자격 활성 회원" 게이트가 이 도메인에는 없다. 이 검증(자격 활성 회원·상품 실존)은 앱 계층 파사드(InquiryWriteFacade)가 조율한다 — 도메인은 본문 검증만 소유한다.
- 비밀글 가시성 정책: 비밀글이 아니면 누구나 열람. 비밀글이면 열람자가 작성자 본인(`memberId` 일치) 또는 관리자(`role = ADMIN`)일 때만 본문·답변을 볼 수 있다.
- 마스킹 소유: 비밀글 마스킹은 도메인·애플리케이션 계층이 아니라 노출(응답) 계층이 소유한다 — 도메인 조회는 항상 원문을 담고, 공개 표면의 응답 조립이 열람 가능 여부를 판정해 비열람자에게는 본문·답변을 가린다. 관리자 표면은 관리자 역할 게이트가 이미 접근을 한정하므로 마스킹하지 않는다(전문 노출).
- 정렬: UUIDv7 PK가 생성 시각 순서를 담아, ID 내림차순이 최신 문의 우선 정렬을 겸한다(별도 정렬 컬럼 없음).
- 도메인 이벤트 없음.

### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| write | memberId, productId, content, secret | 본문 1~1000자(공백 거부), UUIDv7 채번 | 본문 범위 위반 |
| answer | inquiryId, content | 본문 1~1000자(공백 거부), 기존 답변 덮어씀 | 문의 미존재; 본문 범위 위반 |
| getProductPage | productId, pageable | 상품별 최신 문의 우선 페이지 | — |
| getAnswerStatusPage | answered, pageable | 답변 유무로 필터한 전체 최신 문의 우선 페이지 | — |

반환 형상·거부의 예외 매핑·서비스 역할 배치·네이밍은 `docs/coding-conventions.md`가 소유.

## 크로스 도메인 정책 (체크아웃·취소·반품 환불)

여러 도메인을 잇는 흐름의 정책이다. 조율은 앱 계층(파사드)이 하고, 각 도메인 서비스는 자기 트랜잭션만 소유한다(파사드는 트랜잭션을 열지 않는다). 결제 승인이 동기 확정이라 결과를 파사드가 즉시 알므로, 핵심 정합은 파사드의 동기 조율 + 실패 시 동기 보상으로 처리하고, 유실돼도 핵심 정합을 깨지 않는 후처리(장바구니 비우기·재입고 알림)만 도메인 이벤트로 뺀다(발행은 아웃박스 경유 — 아래 아웃박스 절). 동기 경로가 결과를 기록하지 못한 잔여(응답 유실·프로세스 중단)는 미확정 결제 리컨실이 닫는다.

### 체크아웃 (주문 생성 → 결제)

체크아웃은 라인 출처가 다른 세 진입점과 부작용 없는 미리보기를 공유 조율 로직 위에 둔다.

| 진입점 | 라인 출처 | 장바구니 영향 | 부재·불일치 시 |
|---|---|---|---|
| 체크아웃(전체) | 장바구니 전체 라인 | 결제 완료 후 `OrderPaid` 소비로 정리 | 장바구니가 비어 있으면 거부 |
| 체크아웃(선택) | 장바구니 라인 중 요청 `variantIds`만 | 위와 동일(주문된 라인만 정리, 미선택 라인은 잔류) | 요청 변형이 장바구니에 없으면 전체 거부 |
| 바로구매 | 요청 라인(변형ID·수량) 직접 | 없음(장바구니 조회·변경 안 함) | 해당 없음(장바구니 의존 없음) |
| 미리보기 | 위 전체/선택과 동일 게이트 | 없음(부작용 없음) | 체크아웃과 동일 거부 사유 |

- 세 진입점 모두 검증(변형 ACTIVE·상품 ON_SALE·재고 SELLABLE·쿠폰 적용성)과 금액 산식(할인·payAmount)이 동일하다 — 라인을 스냅샷 리스트로 만드는 단계만 다르고 그 뒤는 완전히 공유한다. 미리보기는 검증+금액 산출(totalAmount·discountAmount·shippingFee·payAmount)까지만 실행하고 주문 생성 이하 단계를 실행하지 않는다(주문·재고 차감·쿠폰 사용·결제 요청 어느 것도 없음).

전진 경로(파사드, 동기, 각 서비스 자기 트랜잭션):

1. 검증(읽기): 회원 자격 활성(`status = ACTIVE`·미탈퇴); 장바구니 기반이면 대상 라인이 비어 있지 않을 것(선택 시 선택 변형이 장바구니에 모두 있을 것); 각 라인의 변형 ACTIVE ∧ 변형의 상품 ON_SALE·미삭제 ∧ 재고 `status = SELLABLE`이고 수량이 주문 수량 이상; 쿠폰이 있으면 본인·ISSUED·사용 기한(`expiresAt`)·(할인 전)최소주문금액·산출 할인 > 0 충족. 상품 ON_SALE·변형 ACTIVE(카탈로그 가시성의 그룹·SKU 두 수준)과 재고 SELLABLE(재고 가용성)은 서로 다른 게이트다. 변형·상품·재고 조회의 부재·삭제·미시딩은 예외가 아니라 주문 불가로 강등한다. 할인액·payAmount 계산. 어느 라인이든 주문 불가·재고 부족이면 전체 체크아웃을 거부한다(선택된 라인 묶음 안의 부분 주문 없음). 문제 라인은 자동 제거하지 않으며 사용자가 장바구니에서 직접 뺀다.
2. 주문 생성(PENDING): 파사드가 각 라인을 `variantId`→변형(단가·optionLabel·productId)→상품(상품명)으로 해소해 스냅샷을 만들고(단가는 변형 현재가로 서버가 채운다 — 클라이언트 값 불신), 배송지 스냅샷·서버 계산 할인과 함께 `place`에 넘긴다. `place`(order 도메인)는 변형을 로드하지 않는다(1TX 1애그리거트). Order가 totalAmount 자기 계산·discount ≤ total 가드. orderId 확정. 이 PENDING 주문이 이후 부작용의 사가 앵커다.
3. 재고 차감: 라인별 `deduct(variantId, qty)`. 어느 라인이든 실패하면 주문 취소(→CANCELLED) 후 앞서 차감한 라인을 복원하고 중단. 전 라인 차감이 완료되면 주문에 차감 완료 마커(`markStockDeducted`)를 기록한다 — 스윕·리컨실 보상의 재고 복원 증거다. 마커 기록 실패도 같은 보상(취소 후 복원)을 태운다(이 시점엔 전 라인이 차감돼 전량 복원이 정확하다).
4. 쿠폰 확정(있으면): `use(issuedCouponId, orderId)` → ISSUED→USED. 실패(동시 사용 등) 시 주문 취소 후 재고 복원하고 중단.
5. 결제: `payAmount == 0`이면 PG 생략·자동 APPROVED. 아니면 `PaymentGateway.approve`.
   - APPROVED → 결제 APPROVED 기록, `Order.markPaid`(PENDING→PAID).
   - FAILED/예외 → 동기 보상: 주문 취소(→CANCELLED) → 쿠폰 복원(USED→ISSUED) → 재고 복원. 결제 FAILED 기록. 보상은 스윕·리컨실과 같은 취소-선행 순서다 — 취소의 1회성 전이가 복원을 게이트해, 취소가 실패하면 복원 없이 PENDING이 남는다. 잔여 인계는 payment 행 상태에 따른다: 행 없음(재고·쿠폰 단계 실패)은 PENDING 스윕이 직접 보상하고, REQUESTED는 결제 리컨실이 취소 전이 후 복원을 태우며, 종결 기록된 결제(FAILED 기록 후 취소 실패, APPROVED 기록 후 markPaid 전 중단)는 PENDING 스윕이 발견해 결제 리컨실 확정 경로에 위임한다(FAILED × PENDING → 취소 전이 후 복원, APPROVED × PENDING → markPaid 완결). 스윕·리컨실 보상의 재고 복원은 차감 완료 마커가 게이트한다 — 증거 없는 잔여(차감 전·차감 중 중단)는 복원을 생략해 과복원(재고 증식→오버셀)이 없고, 부분 차감분은 과소복원(팬텀 품절)으로 남아 운영 대사 대상이다.
6. 주문 PAID 커밋: `markPaid` 전이와 같은 트랜잭션에서 `OrderPaid`가 아웃박스 행으로 기록된다(커밋과 원자적). 별도 배치 앱의 릴레이가 커밋된 행을 재발행하면 장바구니 `removeItems`(주문된 변형 라인) 리스너가 소비한다(멱등). 장바구니 정리는 체크아웃 응답 이후의 비동기 후처리다.

- 주문 앵커: 주문 PENDING을 재고 차감 전에 만들어, 차감·쿠폰 사용 등 모든 부작용이 조회 가능한 orderId에 걸린다. 차감 후 크래시가 나도 유실이 관측·복구 가능하다(주문 없는 차감이 아니다).
- 보상은 단일 소유다: 결제 성공 전 모든 실패(재고·쿠폰·결제)는 파사드가 그 콜스택에서 동기 보상한다. 이벤트 리스너와 파사드가 같은 실패를 경쟁 보상하는 이원화가 없다.
- 도메인 이벤트로 빼는 후처리는 정합성 비필수 지점뿐이다 — `OrderPaid` → 장바구니 비우기(dead letter로 미전달이 남아도 장바구니가 남을 뿐이라 재체크아웃이 같은 상품을 재주문할 수 있으나 사용자 개시·가시). 현행 도메인 이벤트 전체 목록은 도메인 이벤트 명세 절이 소유한다.

### 취소·환불 (결제 후, 주문 전체)

사용자가 PAID 주문을 전체 취소하는 흐름(파사드, 동기):

- 출고 차단 선행: 취소 개시 마커(`requestCancellation`)를 PG 환불 앞에 커밋해 취소 진행 중 주문의 `ship`을 거부한다. 마커 커밋은 주문 낙관락으로 `ship`과 직렬화된다 — `ship`이 먼저 커밋되면 개시가 거부돼 취소가 환불 전에 중단되고(잔여 없음), 마커가 먼저 커밋되면 이후 `ship`이 거부된다. 환불 커밋과 주문 취소 전이 사이에 `ship`이 끼어들어 남던 환불 고아(결제 CANCELLED × 주문 PAID+SHIPPED — 돈은 환불, 상품은 출고, 자동 회복 없음)를 원천 차단한다.
- 이중 가드: 결제 취소(`Payment.cancel`, 가드 APPROVED→CANCELLED 1회)로 환불을 먼저 하고, 성공 시 주문 취소(`Order.cancel`, 가드 PAID→CANCELLED 1회)가 재고 복원(라인 `variantId`로) + 쿠폰 복원(USED→ISSUED)을 게이트한다. `pgTransactionId == null`(PG 미호출 승인) 결제는 환불 호출을 생략한다.
- 환불이 복원의 선행조건이다: 환불 실패 시 주문은 PAID로 남고 복원하지 않는다(클라이언트 재시도). 이는 보상 코레오그래피이며 무손실 보장은 범위 밖이다.
- 부분·전체 상호 배타: 부분 취소·부분 반품 이력이 있는 주문(`refundedAmount != 0`)은 전체 취소 개시(`requestCancellation`)가 거부된다 — 남은 라인은 라인 단위로만 처리한다(이중 환불·이중 재고 복원 방지). 반대로 전체 취소가 개시된 주문(`cancelRequestedAt != null`)은 라인 취소 개시가 거부된다.
- 마커 잔여(개시 커밋 후 중단 — 환불 전이든 후든 주문은 PAID + 마커로 남는다)는 이 파사드의 재시도가 소유한다: 재개시는 no-op, 이미 CANCELLED인 결제는 관용 통과, 주문 취소 전이와 복원을 완결한다. 출고가 차단돼 재시도가 SHIPPED에 막히지 않으므로 재시도는 항상 완결에 도달한다. 재시도가 오지 않는 동안 주문은 출고 불가로 남는다 — 취소를 개시한 주문을 출고하지 않는 것이 의도된 차단이고, 출고 시도의 거부(409)가 잔여를 운영에 노출한다(스윕이 이 잔여에 개입하지 않는 근거: PAID 주문이라 PENDING 스윕 밖이고, 결제가 APPROVED·CANCELLED 종결 기록이면 REQUESTED 리컨실 밖이다. 결제가 REQUESTED로 남는 코너 — 리컨실 `markPaid` 완결 후 결제 기록 전 중단에 취소가 겹친 경우 — 는 리컨실이 결제 기록만 확정하고 주문측은 손대지 않아 차단·재시도 소유가 유지된다).
- 재고 복원 정책(재고 도메인 `restore`가 참조하는 크로스 도메인 정책)은 이 취소 파사드가 소유한다: 복원은 주문 취소의 1회성 전이가 이 호출에서 실제 일어났을 때만 탄다. 이미 CANCELLED인 주문은 진입 시 관용 통과해 결제 취소·복원을 재실행하지 않고(완결 후 재호출이 재고를 증식시키지 않음), 환불 커밋 후 주문 취소가 실패한 재시도는 이미 CANCELLED인 결제를 no-op 통과해(PG 환불 재호출 없음, 환불 최대 1회) 취소 전이와 복원을 완결한다.
- 복원 순서는 쿠폰 복원을 재고 복원(가산) 앞에 둔다. 쿠폰 복원은 `restoreUse(issuedCouponId, orderId)`가 사용-주문 일치를 검증해, 취소 완결 후 다른 주문에 재사용된 발급분을 풀지 않는다(크로스-주문 재장전 차단).
- 환원 불가능한 잔여: 취소 전이 커밋과 복원 완료 사이의 중단·복원 루프 중간 실패는 남은 재고 라인·쿠폰의 복원을 유실한다(쿠폰은 USED로 잠겨 고객 손실) — 재호출은 이미 CANCELLED인 주문을 관용 통과하므로 복원을 완결하지 않는다. durable한 라인별 복원 진행 비트가 없어(주문 PAID/CANCELLED가 유일 신호) 라인별 exactly-once는 불변식 안에서 도달 불가라, 이중 복원(재고 증식·오버셀)보다 복원 유실(팬텀 품절, 운영 대사로 회복)을 잔여로 택했다. 멱등 필터가 같은 `Idempotency-Key`의 TTL 창 내 재요청을 409로 거부하는 것(키 저장소는 Redis — 재시작·다중 인스턴스에서도 유지)은 재전송 자체를 좁히는 보조 방어다.

### 라인 취소·라인 반품 (부분 집행 조율)

라인 단위 부분 취소(사용자)와 라인 반품 승인(관리자)의 조율이다. 전체 경로와 같은 원칙(환불 선행 → 복원, 1회성 전이 게이트)을 라인 단위로 따른다:

- 개시: 도메인 전이(`beginLineCancellation`/`beginLineReturn`)가 라인 환불액을 확정(안분 산식은 §6 소유)하고 `refundedAmount`에 가산한다 — 누계 가산이 주문 루트 버전을 올려 동시 라인 처리를 직렬화한다.
- 결제 부분 환불: 확정 환불액으로 `Payment.cancelPartially`(누계 단조 동기화, 재시도 멱등 — §7 소유)를 거친다. 부분 환불 누계가 결제액에 도달하면 결제는 CANCELLED로 종결된다.
- 완결: 라인 완결 전이(`completeLineCancellation`/`completeLineReturn`)가 라인을 종결하고, 그 라인의 재고만 복원한다. 전 라인이 종결되면 주문 전체가 CANCELLED(전 라인 취소) 또는 REFUNDED(취소·반품 혼합 종결)로 수렴하고, 수렴 시 적용 쿠폰이 있으면 함께 복원된다. 수렴을 일으키는 완결 호출만 주문 루트 필드(status 등)를 바꿔 루트 버전을 올린다 — 동시에 같은 라인을 완결하려는 중복 호출이 경합하면 낙관락이 한쪽만 커밋시킨다(§6). 수렴이 아닌 일반 완결은 라인만 갱신해 루트 버전에 영향이 없다.
- 부분·전체 상호 배타(위 취소·환불 절)와 라인 상태의 1회성 전이(§6 `OrderLineStatus`)가 중복 집행을 막는다. 부분 환불 이력이 있는 결제는 전액 취소(`cancel`)가 거부돼(§7) 결제측에서도 이중 환불이 차단된다.

### 반품 환불 (배송 완료 후)

DELIVERED 주문의 반품 환불에는 두 경로가 있다 — 회원 반품 요청 → 관리자 승인/거절 워크플로와, 요청 없이 관리자가 즉시 실행하는 단일 액션. 두 경로 모두 도메인 계층에서는 하나의 전이(`refund`)를 공유한다:

- 반품 요청 워크플로(전체): 회원이 배송 완료된 본인 주문에 사유와 함께 반품을 요청한다(`requestReturn` → returnStatus REQUESTED). 관리자는 승인(파사드가 주문의 `returnReason`을 읽어 `refund()` 실행 — 환불·복원 완결, returnStatus COMPLETED 부수 전이)하거나 거절(`rejectReturn` → REJECTED, 주문은 PAID·DELIVERED로 남고 재요청 가능)한다.
- 반품 요청 워크플로(라인): 회원이 개별 라인에 반품을 요청한다(`requestLineReturn` → 라인 RETURN_REQUESTED). 관리자는 승인(라인 환불액 확정 `beginLineReturn` → 결제 부분 환불 → `completeLineReturn` 완결·그 라인 재고 복원 — 위 라인 조율 절)하거나 거절(`rejectLineReturn` → 라인 ORDERED 복귀, 재요청 가능)한다.
- 관리자 단일 액션(전체): 요청을 거치지 않고 즉시 전체 반품 환불한다. 취소 파사드와 같은 이중 가드다 — 결제 취소(`Payment.cancel`, 가드 APPROVED→CANCELLED 1회)로 환불을 먼저 하고, 성공 시 주문 환불(`Order.refund`, 가드 PAID ∧ DELIVERED → REFUNDED 1회)이 재고 복원 + 쿠폰 복원을 게이트한다. 환불 실패 시 주문은 PAID로 남고 복원하지 않는다.
- 이중 환불은 두 1회성 전이가 구조적으로 거부한다 — 이미 REFUNDED인 주문은 진입 시 관용 통과해 복원을 재실행하지 않고, 결제 취소 커밋 후 주문 환불이 실패한 재시도는 이미 CANCELLED인 결제를 PG 재호출 없이 통과해(환불 최대 1회, PG 멱등 키 이중 방어) 환불 전이와 복원을 완결한다(취소 파사드와 같은 전이 게이트·복원 순서·잔여 한계). 라인 반품 이력이 있는 주문의 전체 환불은 `refund` 자체가 거부한다(§6 — 전 라인 ORDERED 요구·부분 취소 이력 가드).
- 재고 복원: 취소와 같은 `restore`를 쓰되 근거가 다르다 — 취소는 미출고 차감분의 원복이고, 반품은 회수 상품의 재판매 가정이다(회수·검수 절차가 범위 밖이라 파사드가 즉시 복원). 재판매 불가 상품의 재고 차감은 운영 수동 조정으로 남긴다.
- 쿠폰 복원: 취소와 같은 `restoreUse`(USED→ISSUED)를 쓴다. 전액 환불이 거래 전체를 원상회복하므로 쿠폰만 소진되면 고객 손실이라 복원한다. 배송 후라 사용 기한(`expiresAt`)이 경과했을 수 있는 점이 취소와 다르다 — 복원은 상태 복원일 뿐이고 만료는 사용 시점 검증이 거부하므로 경과분 복원은 명목이다(기한 연장·재발급 없음).
- 교환·반품 회수 배송 추적(회수용 택배사·운송장)은 범위 밖이다(범위는 `REQUIREMENTS.md` 소유).

### 미확정 결제 리컨실·웹훅 확정

응답 유실·프로세스 중단으로 REQUESTED에 남은 결제를 확정하고, 종결 기록된(비REQUESTED) 결제가 남긴 주문측 잔여를 마저 종결하는 흐름(파사드, 주기 스윕·웹훅 공용). 소재는 배치 앱(app-batch)이다 — 스케줄 주기·유예 프로퍼티·분산 락은 `REQUIREMENTS.md` 제약·전제가 소유한다.

- 대상 선별: 생성 후 유예(`payment.reconciliation.stale-after`)가 지나도록 REQUESTED인 결제. 유예가 동기 체크아웃과의 경합을 차단한다 — 유예 내 결제는 체크아웃 스레드가 단독 소유하고, 확정 경로(스윕·웹훅 모두)는 손대지 않는다. 종결 기록된 결제 × 미종결 주문 잔여는 결제 상태만으로는 정상 종결과 구분할 수 없어 이 스윕이 선별하지 못한다 — PENDING 주문 스윕이 발견해 위임하고, 웹훅 재전달도 같은 종결 경로에 닿는다.
- 확정 근거는 항상 PG 거래 상태 조회(inquire)다. 웹훅 페이로드는 결제를 지목하는 트리거일 뿐 결과를 신뢰하지 않는다 — 서명(공유 시크릿 HMAC-SHA256)을 통과한 위조 페이로드도 상태를 왜곡하지 못한다.
- 분기는 PG 거래 × 주문 상태의 함수다.
  - 승인 거래 × 주문 PENDING → 주문 결제완료(`markPaid`) 후 결제 승인 확정 기록. 응답 유실 전 크래시로 멈춘 체크아웃의 완결이다.
  - 승인 거래 × 주문 PAID → 결제 기록만 마저 한다(이전 확정이 결제완료 후 중단된 잔여의 자기 복구).
  - 승인 거래 × 주문 CANCELLED·REFUNDED → 주문은 이미 취소·환불로 종결됐고 지연 승인 청구만 고아로 남았다. PG 환불을 먼저 수행하고 승인·취소 기록을 한 커밋으로 남긴다(`confirmOrphanedApproval`) — 어느 지점에서 중단돼도 결제가 REQUESTED로 남아 다음 스윕이 재시도하고, 환불 재호출은 PG 멱등 키가 흡수한다(승인 기록을 먼저 커밋하면 환불 실패 시 APPROVED로 굳어 REQUESTED 선별에서 이탈하므로 기각). REFUNDED × 미확정 결제는 환불이 결제 취소 선행을 전제해 모델상 도달 불가지만 fail-safe 방향이 같다.
  - 거절 거래 → 보상(주문 취소 → 쿠폰 복원 → 재고 복원) 후 결제 실패 확정(거절 사유).
  - 거래 미도달(NOT_FOUND) → 청구가 PG에 닿지 않아 돈이 움직이지 않았다. 거절과 같은 보상 후 실패 확정(`GATEWAY_ERROR`).
- 종결 기록된(비REQUESTED) 결제가 위임·웹훅으로 도달하면 주문측 잔여를 결제 상태 × 주문 상태의 함수로 종결한다. PG 조회 없이(결제 상태가 이미 기록돼 근거가 된다) 처리한다.
  - APPROVED × PENDING → 주문 결제완료(`markPaid`) 완결. 승인 커밋과 markPaid 사이 중단 잔여다 — 돈이 빠졌으므로 보상이 아니라 완결이 맞다.
  - APPROVED × CANCELLED·REFUNDED → 고아 청구 환불(`cancel`).
  - FAILED × PENDING → 보상 종결(주문 취소 → 쿠폰 복원 → 재고 복원). 거절 기록 후 보상 취소가 실패한 잔여다.
  - 그 외 쌍(APPROVED × PAID 등)은 이미 종결이라 무시한다. CANCELLED 결제 × PAID 주문(환불 커밋 후 주문 취소 실패)은 취소 파사드의 재시도가 소유한다 — 취소 개시 마커가 그 주문의 출고를 차단하므로 재시도가 SHIPPED에 막히지 않는다.
- 멱등: 이미 종결된 결제·주문 쌍은 조용히 통과한다 — 반복 스윕·중복 웹훅 전달이 무해하다. 보상의 복원은 주문 취소의 1회성 전이 뒤에만 태워 반복 실행이 이중 복원하지 않고, 주문측 효과는 상태 가드로 재실행을 건너뛴다. 보상의 재고 복원은 추가로 차감 완료 마커가 게이트한다 — payment 행은 체크아웃이 마커 커밋 뒤에만 만들므로 이 경로의 잔여는 증거가 있는 게 정상이지만, 그 보장이 체크아웃 단계 순서라는 비국소 불변식이라 복원 지점마다 지역 강제한다(증거 없으면 복원 생략).
- 결제 상태 기록을 주문측 효과 뒤 마지막에 둔다. 중간에 중단돼도 결제가 REQUESTED로 남아 다음 스윕이 같은 분기를 재실행한다(자기 복구). 보상의 라인 재고 복원은 일시 낙관락 충돌(동시 체크아웃과의 재고 경합)을 라인 단위 재시도로 흡수한다 — `restore`는 가산·교환법칙이고 충돌한 시도는 롤백되므로 재시도가 안전하다. 취소 전이 커밋 후 복원 유실의 잔여 트리거는 중단(크래시)과 재시도 소진(지속 실패)으로 좁혀진다(무손실 복구 범위 밖).
- 스윕은 결제 단위로 실패를 격리한다 — 한 결제의 확정 실패는 로그만 남기고 남은 대상을 계속 처리하며 다음 스윕이 재시도한다.

### PENDING 주문 스윕 (주문 기준 리컨실)

체크아웃은 주문 PENDING을 사가 앵커로 먼저 커밋한 뒤 재고 차감·쿠폰 확정·결제 요청을 잇는다(order-first). 주문 생성 이후~결제 요청 이전 구간에서 프로세스가 중단되면 PENDING 주문·차감 재고·사용 쿠폰이 남되 payment 행이 없다 — 미확정 결제 리컨실은 REQUESTED 결제만 스윕하므로 이 잔여를 발견하지 못한다(팬텀 품절, 자동 복구 없던 무한 잔존 경로). 이를 닫는 흐름(파사드, 주기 스윕)이다. 소재는 배치 앱(app-batch)이다.

- 대상 선별: 생성 후 유예(`order.reconciliation.stale-after`)가 지나도록 PENDING인 주문. 유예를 결제 리컨실 유예(`payment.reconciliation.stale-after`) 이상으로 둔다 — 진행 중 체크아웃과 경합하지 않고, REQUESTED 행 있는 PENDING이 결제 리컨실에 먼저 잡히도록 시간을 벌어 이중 개입을 막는다.
- 관할은 payment 행 존재·상태로 가른다. 행이 없으면 결제 요청 이전에 중단된 잔여라 이 스윕이 직접 보상 종결한다. REQUESTED 행이 있으면 미확정 결제 리컨실 관할이라 손대지 않는다(그 리컨실의 승인 거래 × PENDING → `markPaid`, 거절·미도달 → 보상 분기가 소유한다). 종결 기록된(APPROVED·FAILED) 행이 있으면 결제측 확정은 끝났는데 주문측 종결이 남은 잔여다 — 결제 리컨실 스윕은 REQUESTED만 선별해 이를 보지 못하므로, 이 스윕이 발견해 결제 리컨실 확정 경로에 위임한다(APPROVED × PENDING → markPaid 완결, FAILED × PENDING → 보상 종결). 유예가 지난 무-payment PENDING엔 새 payment 행이 생기지 않는다 — payment 행을 쓰는 유일 경로가 그 주문의 체크아웃이라, 유예를 넘겨 잔존한 주문의 체크아웃은 이미 중단됐다.
- 보상 종결: 주문 취소(`cancel`, PENDING→CANCELLED, 사유 `CHECKOUT_ABANDONED`) 1회성 전이 선행 → 쿠폰 복원(USED→ISSUED, 멱등) → 재고 복원(가산). 취소·환불 흐름과 같은 순서다.
- 재고 복원은 차감 완료 마커(`stockDeductedAt`)가 게이트한다 — 증거 없는 잔여(place 커밋~차감 완료 사이 중단)는 실제 차감량을 알 수 없어 복원을 생략한다. 과복원(재고 증식→오버셀)보다 과소복원(팬텀 품절)이 안전한 방향으로 채택했고, 복원이 생략된 부분 차감분은 운영 대사로 회복한다(경고 로그로 관측). 쿠폰 복원은 마커와 무관하다 — `restoreUse`가 사용-주문 일치를 자기 검증해 미사용 쿠폰엔 no-op이다.
- 멱등: 스윕 쿼리가 PENDING만 반환하므로 반복 실행이 이미 취소된 주문을 재조회하지 않아 복원이 정확히 한 번이다. 라인 재고 복원은 일시 낙관락 충돌(동시 체크아웃과의 재고 경합)을 라인 단위 재시도로 흡수한다 — 취소 전이 커밋 후 복원 유실의 잔여 트리거는 중단(크래시)과 재시도 소진(지속 실패)으로 좁혀진다(무손실 복구 범위 밖, 리컨실 보상과 같은 한계).
- 스윕은 주문 단위로 실패를 격리하고, 처리 건마다 경고 로그를 남겨 잔존 발생을 관측한다.
- 탈퇴·정지 회원의 PENDING 주문도 같은 경로로 잔존하며(탈퇴는 PENDING 주문을 막지 않는다) 이 스윕이 동일하게 종결한다 — 보상은 재고·쿠폰·주문만 건드려 회원 상태와 무관하다.

### 상품 등록 → 첫 변형·재고 시딩

상품 등록과 첫 변형·초기 재고 생성을 잇는 흐름(파사드, 동기):

- 상품 `register`(HIDDEN) → 변형 `create(productId, price, 옵션?)`(DISABLED) → 재고 `create(variantId, 초기수량)` → 변형 `enable`(ACTIVE) → 상품 `show()`(ON_SALE). 첫 변형이 상품의 옵션을 싣는다(옵션 없는 상품만 "" 기본 변형이라 옵션 상품에 유령 기본 변형이 생기지 않는다).
- 변형을 DISABLED로 먼저 두는 이유: 담기 게이트에 재고 검사가 없어 재고 없는 ACTIVE 변형이 카탈로그·담기에 노출되는 것을 막는다. `enable`은 재고 존재를 검증하지 않고 시딩 순서가 보장하며, 체크아웃의 부재→주문 불가 강등이 방어적 심층이다.
- 파괴적 보상이 없다: 실패 시 HIDDEN 상품·DISABLED 변형·재고가 남아 재시도로 복구한다(삭제 보상 없음). 중단 복구는 기존 변형에서 남은 단계를 재개한다(재고 미생성이면 `create`, 미활성이면 `enable`) — DISABLED 변형은 비-RETIRED라 같은 옵션으로 변형 `create`를 다시 부르면 유니크에 막히므로 재생성이 아니라 재개다. 재개는 기존 변형의 가격을 유지하고 재고가 이미 있으면 초기수량을 쓰지 않는다. 완결(ACTIVE) 동일 옵션 변형은 중복으로 거부한다. 관리자가 비활성화한 동일 옵션 변형에 `addVariant`를 부르면 같은 경로로 재활성화된다 — 시딩 중단과 구분할 증거가 없어 재개를 우선한다. Product·ProductVariant·Stock 세 루트에 걸쳐 원자적이지 않다.
- 추가 변형(이미 ON_SALE 상품): 변형 `create`(DISABLED) → 재고 `create(variantId, 초기수량)` → `enable`. enable 전까지 주문 불가라 재고 없는 ACTIVE 변형 창이 없다.
- 변형은 재고 파트너가 있고 나서만 ACTIVE가 되므로, 주문 가능 조건 판정(재고 조회 포함)이 재고 없는 ACTIVE 변형을 만나지 않는다.

### 회원 탈퇴 가드 (미배송 주문)

회원 탈퇴 요청을 처리하는 흐름(파사드, 동기):

- 파사드가 주문 조회로 해당 회원의 미배송 PAID 주문(status = PAID ∧ fulfillmentStatus ≠ DELIVERED) 존재를 확인하고, 있으면 탈퇴를 거부한다. 없을 때만 회원 `delete`를 호출한다. PENDING 주문은 막지 않는다(결제 전이라 방치 가능).
- 이는 앱 계층 오케스트레이션 정책이지 member 도메인 불변식이 아니다 — member는 order에 컴파일 의존하지 않으므로(빌드 강제), 크로스 도메인 규칙은 파사드가 소유한다. 아웃바운드 포트가 아니라 파사드가 order 도메인을 조회해 게이트한다.
- 탈퇴 비연쇄(회원 §1)와 양립한다 — 탈퇴가 주문을 연쇄 정리하지 않되, 미배송 주문이 있으면 탈퇴 자체를 선행 거부할 뿐이다.

### 리뷰 작성 자격 (구매확정 게이트)

리뷰 작성을 order 도메인 조회로 게이트하는 흐름(파사드, 동기):

- 파사드가 해당 회원·상품 조합의 결제 완료(PAID) ∧ 배송 완료(DELIVERED) 주문 라인 존재를 확인하고, 있을 때만 리뷰 `write`를 호출한다. review 도메인은 이 자격을 모른다(회원 탈퇴 가드와 같은 파사드 소유 크로스 도메인 규칙).

### 주문 검색 (관리자, query-order 모듈)

회원 이메일과 주문 상태를 조합하는 관리자 검색이다. member·order 두 애그리거트에 걸쳐 단일 도메인 파사드 합성이 성립하지 않아 별도 조회 모듈(query-order)이 소유한다:

- 입력: 회원 이메일(정확 일치), 선택적 OrderStatus, 페이지.
- 필터: `member.email = :email ∧ member.deletedAt IS NULL ∧ (status 지정 시 order.status = :status)`. 활성(미탈퇴) 회원만 대상이라 탈퇴 회원 이메일은 항상 빈 페이지다(존재 누출 방지).
- 정렬: `order.id` 내림차순 — UUIDv7 PK가 생성 시각 순서를 담아 최신 주문 우선 정렬과 동치다(별도 정렬 컬럼 없음).
- 반환: orderId·orderNumber·memberId·memberEmail·status·fulfillmentStatus·payAmount·orderedAt(=주문 createdAt).
- 구현: `order`↔`member`를 `member.id = order.memberId` 논리 조인(물리 FK 없음)으로 직접 잇는다. member 도메인의 리포지토리·엔티티에 order 모듈이 컴파일 의존하지 않도록 조회 모듈이 두 도메인의 쿼리 타입에만 접근한다(모듈 구조는 `docs/architecture.md` 소유).

### 아웃박스 (전달 보장 메시징)

도메인 이벤트 발행이 공유하는 전달 경로다. 계약(발행 포트·저장소·이벤트 타입 해석)은 `common-event`가, 구현(저장·재전달·dead letter, `messaging.outbox` 스키마)은 `infra-messaging`이 소유한다(메커니즘 상세는 `docs/architecture.md` 소유).

- 발행 경로: 발행 도메인 서비스가 자기 상태 변경과 같은 트랜잭션 안에서 발행 포트를 호출한다. 구현은 활성 트랜잭션이 없으면 즉시 거부하고, 있으면 `messaging.outbox` 테이블에 행을 INSERT한다 — 발행이 발행 주체의 커밋과 원자적이라 커밋-발행 사이 유실 창이 없다.
- 릴레이: 배치 앱(app-batch)만 릴레이를 켠다(다른 앱은 꺼져 있음 — 중복 재발행 방지). 주기 폴링으로 커밋된 미발행 행을 생성순으로 읽어 in-process 재발행하고 발행 표시한다.
- 보장 수준: 전달은 최소 1회(at-least-once) — 발행 표시 전 크래시는 재전달된다. 소비자는 멱등해야 한다. 행별 재시도 상한(10회) 도달 시 dead letter로 격리해 그 이후 폴링에서 제외한다(운영 관측·수동 개입 대상 — 완전한 무손실은 아니다).
- 대상 이벤트: 현재 `OrderPaid`(order 도메인 → 장바구니 라인 제거)·`StockRestocked`(stock 도메인 → 위시리스트 재입고 알림) 두 종 모두 이 경로를 경유한다. 아웃박스는 데이터베이스당 하나이며 도메인별로 나누지 않는다.

### 정합·보장 수준

- 결제 이후 핵심 상태(PAID/CANCELLED)는 파사드가 동기로 확정하므로 동기 경로엔 "결제됐는데 PENDING" 같은 창이 없다. 동기 경로가 기록하지 못한 잔여(응답 유실·중단)는 유예 + 스윕 주기 안에 수렴한다 — REQUESTED 결제는 결제 리컨실이 PG 상태 조회로, 결제 요청 이전 중단으로 payment 행 없이 남은 PENDING 주문은 PENDING 스윕이 보상 종결로, 결제는 기록됐는데 주문측 종결이 남은 잔여(승인 커밋 후 markPaid 전 중단, 거절 기록 후 보상 취소 실패)는 PENDING 스윕이 발견해 결제 리컨실 확정 경로가 닫는다.
- 도메인 이벤트(`OrderPaid`·`StockRestocked`)는 아웃박스 행 기록이 발행 주체의 커밋과 원자적이라 커밋-발행 사이 유실 창이 없다. 릴레이 전달은 at-least-once이고 소비는 멱등이다(장바구니는 집합 제거의 구조적 멱등, 재입고 알림은 eventId dedup). 재시도 상한 초과분은 dead letter로 격리돼 운영 개입 전까지 미전달로 남는다 — 완전한 무손실은 아니되, 미전달의 영향은 장바구니 잔존·알림 미발송뿐이라 핵심 정합(돈·재고·주문 상태)을 깨지 않는다.
- 복원 정확성: 모든 재고·쿠폰 복원은 주문의 1회성 전이(`PENDING·PAID → CANCELLED` 또는 `PAID → REFUNDED`)가 게이트한다. `restore`는 가산·교환법칙이라 낙관락 충돌 시 재시도해도 안전하지만 멱등은 아니므로(커밋된 복원을 재전달하면 이중 가산), 이 전이 가드가 정확히-1회 복원을 보장한다. 전이 가드 자체는 load-check-write라 동시 중복 전이가 둘 다 가드를 통과할 수 있으므로, 주문·결제 낙관락(`@Version`)이 한쪽만 커밋시켜 가드의 1회성을 동시 경합에서도 유지한다(진 쪽은 복원 없이 충돌로 끝난다). 라인 단위 집행도 같은 원리다 — 라인 상태(`OrderLineStatus`)의 1회성 전이가 라인 복원·부분 환불을 게이트하고, 라인 환불 누계(`refundedAmount`) 가산이 주문 낙관락으로 직렬화된다. 스윕·리컨실 보상의 재고 복원은 전이 게이트에 더해 주문 단위 차감 완료 마커(`stockDeductedAt`)가 게이트한다 — 증거 없이는 복원하지 않아 차감 전·차감 중 중단 잔여가 재고를 증식시키지 않는다(부분 차감분은 팬텀 품절로 남아 운영 대사 대상).
- 크로스 트랜잭션 부분 실패(예: 보상 도중 프로세스 크래시)의 무손실 복구는 범위 밖이다. 낙관락 충돌 재시도 수렴(가산·재시도 안전)과 크래시 리플레이 멱등(제공 안 함)은 구분된다.

## 도메인 이벤트 명세

현행 도메인 이벤트는 `OrderPaid`·`StockRestocked` 두 종이며, 둘 다 아웃박스 절의 같은 발행·릴레이 경로를 공유한다. 이벤트 record 구조·발행 포트·릴레이 배선·멱등 소비 키 메커니즘은 `docs/architecture.md`가 소유한다.

### OrderPaid

| 필드 | 타입 | 설명 |
|---|---|---|
| orderId | UUID | 결제 완료된 주문 |
| memberId | UUID | 소비자가 비울 장바구니를 특정 |
| orderedVariantIds | Set\<UUID\> | 비울 라인의 변형들 |

- 최소 페이로드다: 소비자(장바구니 `removeItems`)가 필요로 하는 것만 담는다. `orderedVariantIds`는 체크아웃과 소비 사이에 사용자가 담은 라인을 보존하기 위함이다(주문된 변형의 라인만 제거). 소비 의미는 주문된 variantId의 라인 전체 제거(수량 인지 아님).
- 발행: order 도메인, `Order.markPaid` 전이와 같은 트랜잭션의 아웃박스 행 기록(커밋과 원자적). 릴레이가 커밋 이후에만 재발행하므로 소비자는 항상 커밋된 결과만 본다.
- 소비: cart 도메인 리스너 — 주문된 변형 라인을 장바구니에서 제거(`removeItems`). 집합 제거는 이미 제거된 라인 재삭제가 no-op이라 구조적으로 멱등이다(별도 처리 이력 없이 at-least-once 재전달을 흡수).

### StockRestocked

| 필드 | 타입 | 설명 |
|---|---|---|
| eventId | UUID | 발행 측이 생성한 이벤트 식별자 — 소비 멱등 처리의 dedup 키 |
| variantId | UUID | 판매 가능으로 전환된 변형 식별자 |
| occurredAt | Instant | 발행 측이 기록한 발생 시각 |

- 발행: stock 도메인, `Stock.markSellable`(SOLD_OUT→SELLABLE 전이 = 알림 대상 재입고) 성공 시 같은 트랜잭션의 아웃박스 행 기록. 수량 증량(`increase`)·그 외 전이는 발행하지 않는다. 발행 측은 매 성공 호출마다 새 `eventId`를 발급한다.
- 소비: wishlist 도메인 리스너 — 해당 상품을 찜한 미탈퇴 회원에게 재입고 알림 메일을 발송한다. 집합 제거처럼 자연 멱등이 아니라 처리 기록(`RestockNotification.eventId` 유니크)을 명시적 dedup으로 써 재전달의 메일 재발송을 막는다(소비 정책 상세는 §8 소유).

## 상태 전이 요약

| 도메인 | 상태 흐름 |
|---|---|
| member | ACTIVE ↔ SUSPENDED. 탈퇴는 deletedAt |
| member(주소록) | 상태 없음(기본 배송지는 boolean, 삭제는 물리 삭제) |
| product(그룹) | ON_SALE ↔ HIDDEN. 논리삭제 deletedAt |
| product(변형) | ACTIVE ↔ DISABLED, {ACTIVE, DISABLED} → RETIRED. 최초 DISABLED |
| product(카테고리·이미지) | 상태 없음(카테고리는 논리삭제 deletedAt, 이미지는 물리 삭제) |
| stock | SELLABLE ↔ SOLD_OUT, {SELLABLE, SOLD_OUT} → DISCONTINUED. 소진은 수량 파생. SOLD_OUT→SELLABLE 전이 시 `StockRestocked` 발행 |
| cart | 상태 없음 |
| coupon(정책) | ACTIVE ↔ DISABLED |
| coupon(발급분) | ISSUED ↔ USED (취소 시 복원), ISSUED → REVOKED (관리자 무효화, 종료 상태) |
| order(결제) | PENDING → PAID → CANCELLED, PENDING → CANCELLED, PAID → REFUNDED(이행 DELIVERED에서만). 라인 수렴 진입 포함 |
| order(이행) | NOT_STARTED → PREPARING → SHIPPED → DELIVERED, PREPARING ↔ ON_HOLD |
| order(라인) | ORDERED → CANCELLING → CANCELLED, ORDERED → RETURN_REQUESTED → RETURNING → RETURNED, RETURN_REQUESTED → ORDERED(거절, 재요청 가능) |
| order(반품 요청) | 없음 → REQUESTED → REJECTED(재요청 가능), REQUESTED → COMPLETED(`refund` 부수 전이) |
| payment | REQUESTED → APPROVED → CANCELLED, REQUESTED → FAILED. 부분 환불 누계 도달도 같은 APPROVED → CANCELLED 간선 |
| wishlist | 상태 없음(찜은 물리 삭제, 알림 처리 기록은 eventId 유니크) |
| review | 전용 상태 없음(관리자 제거·본인 삭제 모두 deletedAt, 주체는 deletedBy로 구분) |
| inquiry | 전용 상태 없음(답변 여부는 answer null 파생) |

## 도메인 용어집

### 영문↔한국어

주석·문서는 아래 한국어 용어만 쓴다. 같은 영문 개념을 다른 한국어로 부르지 않는다.

| 영문 | 한국어 | 나타나는 곳 |
|---|---|---|
| fulfillment | 이행 | `fulfillmentStatus`·`FulfillmentStatus`·`holdFulfillment` |
| variant | 변형(SKU) | `ProductVariant`·`variantId` |
| issued coupon | 발급분 | `IssuedCoupon` |
| checkout | 체크아웃 | 크로스 도메인 조율 |
| direct order | 바로구매 | 장바구니 미경유 체크아웃 |
| sweep | 스윕 | PENDING 주문 정리 |
| reconcile | 리컨실 | 결제 상태 확정 |
| catalog | 카탈로그 | 노출 상품 조회 |
| snapshot | 스냅샷 | `OrderLineSnapshot`·배송지 |
| aggregate root | 애그리거트 루트 | `Order`·`Cart` |
| optimistic lock | 낙관락 | `@Version` |
| soft delete | 소프트삭제 | `deletedAt` |
| hold | 보류 | `HoldReason`·`ON_HOLD` |
| ship | 출고 | `shippedAt`·`ship()` |
| revoke | 무효화 | `IssuedCoupon.revoke()` |
| return | 반품 | `ReturnStatus`·`requestReturn`·`refund()` |
| line cancellation | 라인 취소(부분 취소) | `beginLineCancellation`·`refundedAmount` |
| restock | 재입고 | 수량 증량은 `increase`, 알림 대상 재입고(판매 재개)는 `markSellable`·`StockRestocked` — 두 오퍼레이션을 구분해 표기한다 |
| wishlist | 찜(위시리스트) | `WishlistItem` |
| inquiry | 문의 | `Inquiry`·비밀글 |
| outbox | 아웃박스 | `messaging.outbox`·릴레이 |
| orderable | 주문가능 | 카탈로그·장바구니 파생 |
| totalAmount | 라인 합계 | 할인·배송비 반영 전 |
| payAmount | 실청구액 | 라인 합계 − 할인액 + 배송비 |
| compensation | 보상 | 동기 보상 경로 |
| gate | 게이트 | 전이·노출 허용 판정 |

### 예약어 divergence

코드마다 갈리지 않도록 표준 divergence를 등재한다. 아래 외 divergence는 임의로 만들지 않는다.

| 도메인 개념 | 엔티티 | 스키마 | 테이블 | 사유 |
|---|---|---|---|---|
| 주문 | `Order` | `ordering` | `orders` | `order`는 SQL 예약어. 스키마·테이블 모두 회피 |
| 주문 라인 | `OrderLine` | `ordering` | `order_line` | — |

나머지 9개 도메인(member·product·stock·cart·coupon·payment·wishlist·review·inquiry)은 예약어가 아니라 스키마·테이블이 도메인/엔티티명과 일치한다(product 스키마는 `product`·`product_variant`·`category`·`product_image` 네 테이블, member 스키마는 `member`·`member_address` 두 테이블, wishlist 스키마는 `wishlist_item`·`restock_notification` 두 테이블을 두며 모두 divergence가 없다).

## 영속·마이그레이션 유의 (구현 시 규칙이 강제)

도메인 모델 확정 사항 중 빌드·기동이 강제하는 항목이다.

- `@Version`: `stock`·`issued_coupon`·`orders`·`payment`·`cart_item`에 두고 그 외 엔티티에는 두지 않는다. version 컬럼 추가·`ddl-auto=validate` 정합은 `docs/entity-persistence.md`가 소유한다.
- 논리 FK 인덱스: 모든 `xxx_id` 참조 컬럼에 인덱스를 Flyway로 생성한다(물리 FK 없음 — 애그리거트 내부 부모 FK cart_item.cart_id·order_line.order_id도 자동 생성되지 않아 포함).
- 유니크 제약(Flyway 강제): member.email(부분 유니크 `WHERE deleted_at IS NULL`), member_address(member_id)(부분 유니크 — 기본 배송지 회원당 1개), product_variant(product_id, option_signature)(부분 유니크 `WHERE status <> 'RETIRED'` — 은퇴 조합 재등록 허용), stock.variant_id, cart.member_id, cart_item(cart_id, variant_id), issued_coupon(coupon_id, member_id), payment.order_id, orders.order_number, wishlist_item(member_id, product_id), restock_notification.event_id, review(member_id, product_id)(부분 유니크 `WHERE deleted_at IS NULL` — 본인 삭제 후 재작성 허용). product_variant 부분 유니크 술어가 enum 문자열 `'RETIRED'`를 참조하므로 상태값을 바꿀 때 인덱스 술어도 함께 갱신한다.
- 스키마 등록: 10개 도메인 스키마 각각을 `db/migration/{name}/`에 두고 `SchemaFlywayFactory`(common-jpa)에 등록한다. 아웃박스의 `messaging` 스키마는 도메인이 아니라 `infra-messaging`이 소유한다.
- 애그리거트 내부 연관(cart_item→cart, order_line→order)도 물리 FK 없이 `NO_CONSTRAINT`로 매핑한다.


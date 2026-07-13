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
- 상태: enum(문자열 저장). 상태 변경은 의도 동사 메서드 + 허용 전이 가드로만 한다(setter 없음). 아무도 전이시키지 않는 "죽은 상태"는 두지 않는다.
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
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 논리삭제(탈퇴) 시각 |

- 상태 enum 없음. 이 범위에서 회원은 "가입됨/탈퇴됨" 두 상태뿐이고 그것은 `deletedAt`으로 표현된다. 인증·관리자 정지가 도입되면 그때 상태를 추가한다(지금은 죽은 상태가 되므로 두지 않는다).

### 값 객체 (Email)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| value | String | 필수 | 이메일 주소. 형식 검증(정규식). 단일 컬럼 매핑(`AttributeConverter`) |

### 정책·불변식

- 가입(register): 필수값은 email, name. 가입 즉시 주문·장바구니를 쓸 수 있다(별도 활성화 절차 없음).
- 이메일 유니크: 활성 회원 사이에서 유니크. 탈퇴한 이메일은 재가입 가능 — DB에서 부분 유니크 인덱스(`WHERE deleted_at IS NULL`)로 강제한다.
- 탈퇴(delete): `deletedAt` 세팅(논리삭제). 탈퇴 회원은 기본 조회에서 제외.
- 주문 자격: 주문·장바구니 담기는 탈퇴하지 않은 회원만(체크아웃·담기에서 검증).

## 2. 상품 (product)

판매 상품의 카탈로그 정보·가격을 소유한다. 재고 수량은 별도 재고 도메인이 소유한다(여기 두지 않는다).

- 애그리거트 루트: `Product`
- 스키마/테이블: `product` / `product`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 상품명 |
| description | String | 선택 | 상세 설명 |
| price | Money(VO) | 필수 | 판매가. 1 이상(무료 상품 미지원) |
| status | ProductStatus | 필수 | 아래 상태표 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |
| deletedAt | Instant | 선택 | 논리삭제 시각 |

### 상태 (ProductStatus)

| 상태 | 의미 |
|---|---|
| ON_SALE | 판매중. 카탈로그 노출·주문 가능 |
| HIDDEN | 숨김. 노출·주문 불가(일시 중지) |

- 전이: `ON_SALE ↔ HIDDEN`. 품절은 상품 상태가 아니라 재고 수량(=0)에서 파생한다(상태로 두지 않는다).

### 정책·불변식

- 등록(register): 필수값 name, price(>0). 최초 상태 ON_SALE. 등록과 초기 재고 생성의 조율은 앱 계층이 한다(재고 도메인·크로스 도메인 정책 참조).
- 가격은 0원 불가.
- 주문 가능 조건: status = ON_SALE 이고 삭제되지 않은 상품만. HIDDEN·삭제 상품은 주문 라인에 담기지 않는다.
- 가격 변경은 이미 생성된 주문에는 영향이 없다(주문은 단가를 스냅샷으로 보관). 장바구니는 상품 현재가를 체크아웃 시점에 조회하므로 가격 변경이 반영된다.
- 논리삭제: `deletedAt`.

## 3. 재고 (stock)

상품별 재고 수량과 그 차감·복원을 소유한다. 동시 주문 경합이 실재하는 도메인이라 낙관락(`@Version`)을 둔다.

- 애그리거트 루트: `Stock`
- 스키마/테이블: `stock` / `stock`

### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| productId | UUID | 필수 | 상품 참조(유니크 — 상품당 재고 1행) |
| quantity | int | 필수 | 가용 수량. 0 이상 |
| version | long | 필수 | 낙관락 버전(기본 0) |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

- 상태 enum 없음. 품절은 quantity = 0에서 파생한다.

### 정책·불변식

- 생성(create): 상품 등록 시 초기 수량으로 1행 생성. 상품당 정확히 1행(productId 유니크). 초기 수량 0 이상.
- 차감(deduct, n): quantity ≥ n 일 때만 quantity -= n. 부족하면 재고부족 도메인 예외(주문 중단). `@Version`으로 동시 차감을 직렬화하고, 충돌 시 409로 응답한다(서버 자동 재시도 없음, 클라이언트 재시도).
- 복원(restore, n): quantity += n. 주문 취소·결제 실패 보상에서 호출. 복원은 가산이라 교환법칙이 성립하므로, 낙관락 충돌 시 재시도해도 오버셀 위험이 없다(복원은 재시도로 안전하게 완료한다).
- quantity는 음수가 될 수 없다(차감 가드가 보장).
- 입고(increase)는 범위 밖. 최소 흐름은 create·deduct·restore로 성립한다.

## 4. 장바구니 (cart)

회원의 구매 예정 상품 목록을 소유한다. 애그리거트 루트+자식(라인) 구조다.

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
| productId | UUID | 필수 | 상품 참조. 한 장바구니 내 유니크 |
| quantity | int | 필수 | 1 이상 |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### 정책·불변식

- 회원당 장바구니 1개. 최초 담기 요청 시 없으면 생성한다(lazy get-or-create). 회원 가입과 결합하지 않는다.
- 담기 필수값: memberId, productId, quantity(≥1).
- 담기 조건: 상품이 ON_SALE 이고 삭제되지 않았을 것. 재고는 담기 시점에 검증하지 않는다(체크아웃 시점에 검증). 장바구니는 "구매 예정" 목록이다.
- 동일 상품 재담기: 새 라인을 만들지 않고 기존 라인 수량을 합산한다(productId는 장바구니 내 유니크).
- 수량 변경: 1 이상으로만. 0으로 만들면 라인 제거로 처리.
- 제거·비우기: 라인 개별 제거, 전체 비우기(clear).
- 총액은 저장하지 않는다. 체크아웃 시 상품 현재가로 계산한다.
- 주문 전환: 체크아웃은 장바구니 전체를 주문으로 전환한다(부분 주문 미지원). 결제 완료 후 주문된 라인을 비운다(비우기는 `OrderPaid` 이벤트 소비로 처리 — 크로스 도메인 정책 참조).

## 5. 쿠폰 (coupon)

할인 정책(쿠폰)과 회원에게 발급된 쿠폰을 소유한다. 정책과 발급분은 생명주기가 달라 서로 다른 애그리거트이고 ID로 잇는다.

- 애그리거트 루트: `Coupon`(정책), `IssuedCoupon`(발급분)
- 스키마/테이블: `coupon` / `coupon`, `issued_coupon`

### Coupon 필드 (정책)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | String | 필수 | 쿠폰명 |
| discountType | DiscountType | 필수 | FIXED(정액) 또는 RATE(정률) |
| discountValue | long | 필수 | FIXED=할인 원, RATE=할인 퍼센트(1–100) |
| minOrderAmount | Money | 필수 | 최소 주문 금액. 0 가능 |
| maxDiscountAmount | Money | 선택 | RATE 쿠폰의 최대 할인 상한 |
| validFrom | Instant | 필수 | 유효 시작 |
| validUntil | Instant | 필수 | 유효 종료 |
| status | CouponStatus | 필수 | ACTIVE 또는 DISABLED |
| createdAt | Instant | 필수 | 생성 시각(Auditing 자동 기록) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing 자동 기록) |

### IssuedCoupon 필드 (발급분)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| couponId | UUID | 필수 | 쿠폰 정책 참조 |
| memberId | UUID | 필수 | 발급 대상 회원 참조 |
| status | IssuedCouponStatus | 필수 | ISSUED 또는 USED |
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

- 발급분 전이: `ISSUED → USED`(사용), `USED → ISSUED`(취소 복원). 만료는 상태가 아니라 `validUntil` 경과로 파생 판정한다(죽은 상태·죽은 배치 없음).

### 정책·불변식

- 발급(issue): ACTIVE 쿠폰이고 유효기간 내일 때만. 회원당 동일 쿠폰 1회 발급(`(couponId, memberId)` 유니크로 강제). 최초 상태 ISSUED.
- 할인 계산: FIXED는 min(discountValue, 주문금액). RATE는 floor(주문금액 × 퍼센트 / 100)이고 `maxDiscountAmount`가 있으면 그 값으로 상한. 어떤 경우든 할인액은 주문금액을 초과하지 않는다(결제금액 음수 불가). 반올림은 버림(floor).
- 적용 조건: (할인 전) 주문금액 ≥ minOrderAmount 이고 발급분이 ISSUED, 본인(memberId) 소유, 유효기간(`validFrom ≤ now ≤ validUntil`) 내.
- 사용(use): 주문 생성 이후 `use(issuedCouponId, orderId)`로 USED 전이하고 usedAt·orderId 세팅. `@Version`으로 동시 중복 사용을 막는다(동시 두 주문이 같은 쿠폰을 사용하면 한쪽만 성공, 다른 쪽은 충돌로 보상됨).
- 만료: `validUntil` 경과분은 사용 시점 검증에서 거부한다(별도 만료 상태·배치 없음).
- 취소 복원: 주문 취소·결제 실패로 주문이 무효가 되면 USED → ISSUED로 복원하고 usedAt·orderId를 clear한다.

## 6. 주문 (order)

주문과 주문 라인을 소유한다. 라인은 주문 시점의 상품명·단가를, 배송지는 주문 시점 값을 스냅샷으로 보관한다(상품·회원 변경과 무관하게 내역을 보존). 정본 애그리거트 예시다.

- 애그리거트 루트: `Order`, 자식: `OrderLine`
- 스키마/테이블: `ordering` / `orders`, `order_line` (예약어 회피 divergence — 용어집 참조)

### Order 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| memberId | UUID | 필수 | 주문 회원 참조 |
| status | OrderStatus | 필수 | 아래 상태표 |
| totalAmount | Money | 필수 | 라인 합계(할인 전) |
| discountAmount | Money | 필수 | 쿠폰 할인액. 0 가능. 서버가 쿠폰 정책으로 계산 |
| payAmount | Money | 필수 | 결제 대상 금액 = totalAmount − discountAmount (≥ 0) |
| issuedCouponId | UUID | 선택 | 적용된 발급 쿠폰 참조 |
| shippingAddress | Address(VO) | 필수 | 배송지 스냅샷(다중 컬럼 `@Embeddable`) |
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
| productId | UUID | 필수 | 상품 참조 |
| productName | String | 필수 | 주문 시점 상품명 스냅샷 |
| unitPrice | Money | 필수 | 주문 시점 단가 스냅샷 |
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

### 정책·불변식

- 생성(place): 라인 1개 이상. 회원은 미탈퇴. 배송지 필수. 최초 상태 PENDING.
- 각 라인은 주문 시점 상품명·단가를, 배송지는 주문 시점 값을 복사한다(스냅샷). 이후 상품·회원 변경이 주문 내역을 바꾸지 않는다.
- 금액 불변식: totalAmount = Σ(라인 unitPrice × quantity). discountAmount는 서버가 쿠폰 정책으로 계산(클라이언트 전달값 불신). payAmount = totalAmount − discountAmount ≥ 0.
- 결제 완료(markPaid): PENDING에서만 PAID로. 전이 후 `OrderPaid`를 커밋 후 발행한다.
- 취소(cancel): PENDING 또는 PAID에서 CANCELLED로. 전이는 1회만 유효하고, 후속 보상(재고·쿠폰 복원)은 전이가 실제 일어난 호출에서만 태운다(중복 취소 무해).
- 생성 후 라인 구성은 불변(수정 불가). 변경이 필요하면 취소 후 재주문.

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
| method | PaymentMethod | 필수 | 결제 수단(범위 내 CARD) |
| pgTransactionId | String | 선택 | PG 승인 후 채워짐 |
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
- 승인: `PaymentGateway.approve` 성공 시 APPROVED + pgTransactionId, 실패 시 FAILED. `payAmount == 0`(전액 할인)이면 PG를 호출하지 않고 APPROVED로 자동 처리한다(pgTransactionId 없음).
- 취소·환불(cancel): APPROVED에서만 CANCELLED로. `PaymentGateway.cancel` 호출. 단 `amount == 0`(PG 미호출 승인)이면 환불 호출을 생략하고 상태만 CANCELLED로 둔다.
- 멱등: 이미 결제된 주문(APPROVED)에 재요청은 거부. 결제 실패로 주문이 취소되면 재체크아웃은 새 주문(새 orderId)을 만든다 — 실패 결제 행은 새 주문을 막지 않는다.
- 도메인 이벤트 없음. 결제 결과는 동기 stub이 호출자(체크아웃 파사드)에게 즉시 반환하므로, 승인/실패 반영은 파사드가 동기로 처리한다(크로스 도메인 정책 참조).

## 크로스 도메인 정책 (체크아웃·취소)

여러 도메인을 잇는 흐름의 정책이다. 조율은 앱 계층(파사드)이 하고, 각 도메인 서비스는 자기 트랜잭션만 소유한다(파사드는 트랜잭션을 열지 않는다). 결제가 동기 stub이라 결과를 파사드가 즉시 알므로, 핵심 정합은 파사드의 동기 조율 + 실패 시 동기 보상으로 처리하고, 유실돼도 무해한 후처리(장바구니 비우기) 하나만 도메인 이벤트로 뺀다.

### 체크아웃 (주문 생성 → 결제)

전진 경로(파사드, 동기, 각 서비스 자기 트랜잭션):

1. 검증(읽기): 회원 미탈퇴; 각 상품 ON_SALE·미삭제; 각 재고가 주문 수량 이상; 쿠폰이 있으면 본인·ISSUED·유효기간·(할인 전)최소주문금액 충족. 할인액·payAmount 계산.
2. 재고 차감: 라인별 `deduct`. 어느 라인이든 실패하면 앞서 차감한 라인을 복원하고 중단(주문 생성 안 함).
3. 주문 생성(PENDING): 라인·배송지 스냅샷, 서버 계산 할인 반영. orderId 확정.
4. 쿠폰 확정(있으면): `use(issuedCouponId, orderId)` → ISSUED→USED. 실패(동시 사용 등) 시 방금 만든 주문 취소 + 재고 복원 후 중단.
5. 결제: `payAmount == 0`이면 PG 생략·자동 APPROVED. 아니면 `PaymentGateway.approve`.
   - APPROVED → 결제 APPROVED 기록, `OrderProcessor.markPaid`(PENDING→PAID).
   - FAILED/예외 → 동기 보상: 재고 복원 + 쿠폰 복원(USED→ISSUED) + 주문 취소(→CANCELLED). 결제 FAILED 기록.
6. 주문 PAID 커밋 후 → `OrderPaid` 발행 → `CartRemover.clear`(주문된 라인) 리스너(멱등).

- 보상은 단일 소유다: 결제 성공 전 모든 실패(재고·쿠폰·결제)는 파사드가 그 콜스택에서 동기 보상한다. 이벤트 리스너와 파사드가 같은 실패를 경쟁 보상하는 이원화가 없다.
- 유일한 도메인 이벤트는 `OrderPaid`(주문이 커밋 후 발행) → 장바구니 비우기다. 비우기는 정합성 비필수·유실 무해(안 비면 UX 마찰뿐)라 최종일관성이 정당한 유일 지점이다.

### 취소·환불 (결제 후)

사용자가 PAID 주문을 취소하는 흐름(파사드, 동기):

- `PaymentGateway.cancel`(환불) 먼저 → 성공 시 주문 취소(PAID→CANCELLED) + 재고 복원 + 쿠폰 복원(USED→ISSUED). `amount == 0` 결제는 환불 호출을 생략.
- 환불이 복원의 선행조건이다(환불 실패 시 복원하지 않는다). 이는 보상 코레오그래피이며, 무손실 보장은 범위 밖이다.

### 정합·보장 수준

- 결제 이후 핵심 상태(PAID/CANCELLED)는 파사드가 동기로 확정하므로 "결제됐는데 PENDING" 같은 창이 없다.
- in-process 이벤트(`OrderPaid`)는 커밋 후 발행하되 무손실 보장이 아니다. 리스너는 멱등하고, 유실 시 장바구니가 남는 것 외 영향이 없다.
- 크로스 트랜잭션 부분 실패(예: 보상 도중 프로세스 크래시)의 무손실 복구는 범위 밖이다. 복원 연산은 가산·멱등에 가깝게 두어 재시도로 수렴하게 한다.

## 상태 전이 요약

| 도메인 | 상태 흐름 |
|---|---|
| member | 상태 없음. 탈퇴는 deletedAt |
| product | ON_SALE ↔ HIDDEN. 논리삭제 deletedAt |
| stock | 상태 없음(수량) |
| cart | 상태 없음 |
| coupon(발급분) | ISSUED ↔ USED (취소 시 복원) |
| order | PENDING → PAID → CANCELLED, PENDING → CANCELLED |
| payment | REQUESTED → APPROVED → CANCELLED, REQUESTED → FAILED |

## 도메인 용어집 (예약어 divergence)

코드마다 갈리지 않도록 표준 divergence를 등재한다. 아래 외 divergence는 임의로 만들지 않는다.

| 도메인 개념 | 엔티티 | 스키마 | 테이블 | 사유 |
|---|---|---|---|---|
| 주문 | `Order` | `ordering` | `orders` | `order`는 SQL 예약어. 스키마·테이블 모두 회피 |
| 주문 라인 | `OrderLine` | `ordering` | `order_line` | — |

나머지 6개 도메인(member·product·stock·cart·coupon·payment)은 예약어가 아니라 스키마·테이블이 도메인/엔티티명과 일치한다.

## 영속·마이그레이션 유의 (구현 시 규칙이 강제)

도메인 모델 확정 사항 중 빌드·기동이 강제하는 항목이다. 구현 계획이 이를 반영한다.

- `@Version` 컬럼: `stock`·`issued_coupon`에 `version BIGINT DEFAULT 0`을 Flyway로 추가한다. `payment`는 `@Version`이 없으므로 version 컬럼을 두지 않는다(`ddl-auto=validate` 정합).
- 논리 FK 인덱스: 모든 `xxx_id` 컬럼(stock.product_id, cart.member_id, cart_item.product_id, order.member_id·issued_coupon_id, order_line.product_id, issued_coupon.coupon_id·member_id·order_id, payment.order_id) 인덱스를 Flyway로 생성한다(물리 FK 없음).
- 유니크 제약: member.email(부분 유니크 `WHERE deleted_at IS NULL`), stock.product_id, cart.member_id, cart_item(cart_id, product_id), issued_coupon(coupon_id, member_id), payment.order_id를 Flyway로 강제한다.
- 스키마 등록: 7개 도메인 스키마 각각을 `db/migration/{name}/`에 두고 `SchemaFlywayFactory`(common-jpa)에 등록한다.
- 애그리거트 내부 연관(cart_item→cart, order_line→order)도 물리 FK 없이 `NO_CONSTRAINT`로 매핑한다.

## 확정된 결정 (요약)

앞선 설계·검증 리뷰를 반영한 결정이다.

- 회원: 상태 enum 없음, 가입 즉시 주문 가능, 탈퇴는 deletedAt, 이메일 부분 유니크로 재가입 허용.
- 상품: 등록 즉시 ON_SALE, 가격 1원 이상, 재고 생성은 앱 계층이 순차 조율.
- 재고: 즉시 차감(예약 모델 아님), 차감은 `@Version` 가드, 복원은 재시도로 안전.
- 쿠폰: 회원당 1회 발급, 사용은 주문 생성 이후 확정, 만료는 유효기간(`validUntil`) 판정(EXPIRED 상태 없음), 취소 시 복원.
- 주문: 라인·배송지 스냅샷, 할인·payAmount 서버 계산, 취소는 PENDING·PAID 모두.
- 결제: `@Version` 없음, 주문당 1행, 0원은 PG 생략 자동 승인, 도메인 이벤트 없음.
- 정합: 핵심은 파사드 동기 조율 + 동기 보상, 유일 이벤트는 `OrderPaid → 장바구니 비우기`.
- Money: `common-core` 순수 값 타입, 컨버터는 `common-jpa` 단일. 배송지 `Address`는 `@Embeddable`.

## 명시적 범위 밖

- 배송 상태·이행(SHIPPED·DELIVERED 등)과 배송 추적.
- 부분 취소·부분 환불·부분 선택 주문.
- 사람이 읽는 주문번호(orderNumber).
- 회원 주소록(배송지는 체크아웃 요청이 매번 제공).
- 인증·로그인(JWT), 회원 자격증명.
- 실 PG·비동기 웹훅과 그에 따른 PENDING 리컨실·아웃박스(내구성 메시징).
- 쿠폰 선착순 발급 한도, 재고 입고(increase).
- 체크아웃 동시 더블서밋 방어(멱등 필터는 추후 `common-web` 도입 시).

# plan — app-api 컨트롤러 Javadoc을 Swagger 단일 출처로 정리

## 배경

`2cb5246`("app-api 주석을 Swagger 단일 출처로 정리")가 `coding-conventions.md`에 규칙을 추가하고 정리를 시작했으나 **admin 컨트롤러에만** 위임 내레이션 제거를 적용했다. v1 비-admin 컨트롤러 7개는 같은 위반이 남았다. 이 작업이 그 누락을 마감한다.

## 적용 규칙 (`docs/coding-conventions.md` "코드 주석·Javadoc")

- **:119** — Swagger 애노테이션이 API 계약을 소유하면 애노테이션이 단일 출처. 같은 내용을 Javadoc으로 중복하지 않는다. 애노테이션에 없는 계약만 남긴다.
- **:112** — Javadoc은 계약(What)을 적고 구현 방식(How)은 적지 않는다.
- **:116** — 협력자의 내부 동작이나 다른 요소의 계약을 되풀이하지 않는다.

## 기준 스타일 (이미 정리된 admin 컨트롤러 = 정답 예시)

- 한 줄 요약 "…엔드포인트다." + **애노테이션에 없는 비자명 불변식만** `<p>` 한 줄.
- 예: `CouponAdminController` = "…관리자 엔드포인트다." + "중지는 신규 발급만 막고 기발급분 사용에는 소급하지 않는다."
- 제거 대상 상투구: `…파사드에 위임`, `도메인 Reader에 위임`, `…예외를 전역 핸들러가 problem+json으로 매핑한다`, `@Tag`/`@ApiResponses`가 이미 소유한 내용의 재서술.

## 범위

- 대상은 `module-apps/app-api`의 **web 계층**(컨트롤러 + request/response DTO)뿐이다. 규칙 :119는 Swagger가 계약을 소유하는 표면에만 적용된다. facade·config·exception은 Swagger가 소유하지 않으므로 이 작업 대상이 아니다.
- request/response DTO는 전수 확인 결과 대부분 `@Schema`에 없는 비자명 의미(예: "적용 불가는 오류가 아니라 사유와 0원으로 싣는다")만 담아 규칙에 부합한다. 이번 작업에서 손대지 않는다.

## 작업 체크리스트 — 컨트롤러

각 항목: `<p>` 블록에서 위임·전역핸들러·`@Tag`/`@ApiResponses` 재서술을 제거하고, "유지"만 남긴다.

- [x] `web/v1/cart/CartController.java`
  - 제거: "뷰 파사드가 …합성", "커맨드 파사드가 …게이트를 적용", "전역 핸들러가 problem+json으로 매핑", 토큰 주체 도출 서술
  - 유지: 요약 한 줄 "장바구니 조회·쓰기 엔드포인트다." (그 외 비자명 불변식 없음 — 라인 합성·현재가는 `CartResponse`가 소유)
- [x] `web/v1/coupon/CouponController.java`
  - 제거: "발급 파사드에 위임 …게이트를 적용", "전역 핸들러가 problem+json으로 매핑"
  - 유지: 요약 한 줄 "쿠폰 발급(셀프 클레임) 엔드포인트다."
- [x] `web/v1/coupon/IssuedCouponController.java`
  - 제거: "도메인 Reader에 위임 …단건·목록 조회", "전역 핸들러가 problem+json으로 매핑", 미리보기 상태 불변 서술(이미 미리보기 핸들러의 `/** 결과는 보증이 아니며 … */`가 소유)
  - 유지: 요약 한 줄만. "미소유는 미존재(404)로 취급"은 **제외** — `@ApiResponse` 404 설명 "발급 쿠폰 없음(미소유 포함)"이 이미 소유(:119). 미리보기 핸들러 `/** 결과는 보증이 아니며 … */`는 애노테이션에 없는 계약이라 유지.
- [x] `web/v1/member/AuthController.java`
  - 제거: "회원 도메인에 위임 …JWT 발급", "전역 핸들러가 401 problem+json으로 매핑"(401·계정 존재 비노출은 `LoginRequest` doc이 소유)
  - 유지: 요약 한 줄 "로그인·토큰 발급 엔드포인트다."
- [x] `web/v1/member/MemberController.java`
  - 제거: "도메인 서비스에 …탈퇴 파사드에 위임", "전역 핸들러가 problem+json으로 매핑", 본인 표면·토큰 도출 서술
  - 유지: 요약 한 줄 + "정지와 탈퇴는 독립 축이라 정지 회원도 탈퇴할 수 있다"(비자명 불변식)
- [x] `web/v1/order/OrderController.java`
  - 제거: "파사드에 …Reader에 얇게 위임 …DTO로 변환", "전역 핸들러가 problem+json으로 매핑", 토큰 도출·401 서술
  - 유지: 요약 한 줄만. "타인 주문은 미존재(404) 취급"은 **제외** — 모든 관련 `@ApiResponse` 404 설명 "주문 없음 또는 타인 주문"이 이미 소유(:119).
- [x] `web/v1/product/ProductController.java`
  - 제거: "각 파사드에 위임 …합성 …컨트롤러는 DTO 변환만", "전역 핸들러가 problem+json으로 매핑"
  - 유지: 요약 한 줄 "상품 목록·상세 조회 엔드포인트다." + "전부 공개(비로그인 쇼핑)"(인증 마커 없음을 설명하는 비자명 사실 — 애노테이션 미소유)

## 검토 체크리스트 — 판단 필요(전면 삭제 아님)

- [x] `web/v1/payment/PaymentWebhookController.java` — 검토 완료, **무변경**.
  - HMAC-SHA256 서명 검증·비-토큰 인증·멱등(중복 통지 무해) 근거는 Swagger가 담지 못하는 정당한 "왜"라 유지한다.
  - "결과 확정은 파사드가 PG 상태 조회로 한다"는 위임 서술이 아니라 "서명 통과 페이로드도 상태를 직접 못 쓴다"는 보안 계약의 근거이므로 유지한다.

## 대상 아님 (기준 예시 — 확인만)

- admin 컨트롤러 7개는 이미 기준 스타일이다. 변경 금지.
  - `CouponAdminController`, `IssuedCouponAdminController`, `MemberAdminController`, `OrderAdminController`, `ProductAdminController`, `ProductVariantAdminController`, `StockAdminController`

## 완료 기준 (DoD)

- [x] 위 7개 컨트롤러의 위임·전역핸들러 상투구가 제거되고 유지 항목만 남는다.
- [x] 다음 grep이 web 계층에서 **빈 결과**다(확인함).
  - `grep -rn "전역 핸들러가 problem+json" module-apps/app-api/src/main/java/com/commerce/api/web`
  - `grep -rnE "파사드에 위임|Reader에 위임|Reader에 얇게 위임" module-apps/app-api/src/main/java/com/commerce/api/web`
- [x] 빌드 게이트 통과(Spotless·NullAway·Error Prone) → `docs/code-quality.md`. (`:module-apps:app-api:spotlessJavaCheck :module-apps:app-api:compileJava` BUILD SUCCESSFUL)
- [ ] 구현과 분리된 관점의 독립 리뷰 후 머지 → `AGENTS.md` 작업 원칙. (미완 — 머지 전 수행)

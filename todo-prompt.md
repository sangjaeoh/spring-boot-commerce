# TODO 착수 프롬프트

`todo.md`의 각 슬라이스를 새 세션에서 착수할 때 그대로 붙여넣는 프롬프트다. 번호는 `todo.md`와 일치한다. 위에서부터 진행한다.

각 프롬프트는 자립적이다 — 서브에이전트는 이 세션 대화를 보지 못하므로 공통 규칙·규칙 요지를 앞에 담았다. 아래 코드블록만 복사해 붙여넣으면 된다.

- 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다 — 착수 시 반드시 전문을 로딩한다. 요지는 프롬프트에 담았으나 판정이 갈리면 정본을 따른다.
- 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이 있다. **이전 규칙을 기억으로 적용하지 말고 정본을 로딩한다.** 메서드 요약은 "시그니처가 말하면 생략"에서 "전부 의무(면제 7종 제외)"로 바뀌었고, 영속 필드·enum 상수 요약 의무가 신설됐으며, 타입 doc은 "조감도 소유"에서 "그 타입이 무엇인지만"으로 좁아졌다.
- 변경은 동작·시그니처 무변경(Javadoc·주석만). 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(외과적 편집).
- 한 슬라이스가 끝나면 `todo.md`에서 해당 항목을 완료 처리하고 다음 번호로 넘어간다.

---

### 1. module-external + module-infra (어댑터 모듈)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"을 module-external·module-infra에 적용한다.
이 두 모듈은 기존 슬라이스 목록(app-api·module-domains·module-common·app-migration)에서 빠져 주석이
1차 규칙 시절 그대로다. 동작·시그니처는 바꾸지 않는다 — Javadoc·주석만 손댄다.

규칙 정본은 위 문서다. 반드시 전문을 로딩한다. 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이
있으니 기억으로 적용하지 말 것(메서드 요약은 "생략 가능"에서 "전부 의무"로, 영속 필드·enum 상수
요약 의무는 신설, 타입 doc은 "조감도 소유"에서 "무엇인지만"으로 바뀌었다). 요지:
- 주석은 한국어. 용어는 DOMAIN_MODEL.md 도메인 용어집에 등재된 것만 쓰고, 미등재 개념은 용어집에
  먼저 올린다. 도메인 개념이 타입 doc에 처음 나오면 `한국어(영문식별자)`로 병기한다.
- 요약은 타입·메서드·영속 필드·enum 상수에 둔다(각 한 문장).
  · 타입 = 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
  · 메서드 = 공개면 호출자 계약(무엇을 보장하나), 내부면 그 메서드가 맡은 일.
  · 필드 = 그 값이 무엇인지만. 그 값이 게이트하는 동작·전이 효과는 적지 않는다.
  · enum 상수 = 의미. 상태 enum이면 진입 조건을 더하고 상수당 한 줄을 넘기지 않는다.
- 요약을 두지 않는 자리(다른 데가 의미를 이미 소유): 상위 계약을 그대로 따르는 오버라이드 / 필드
  반환 한 줄 접근자(get*·is*, 단 has*·can*는 항상 요약을 둔다) / 생성자(생성 계약은 정적 팩토리가
  소유한다 — 팩토리에는 요약을 둔다) / 이름이 곧 쿼리 명세인 파생 쿼리 메서드(@Query가 붙으면 요약을
  둔다) / 테스트 메서드(@DisplayName) / 상수가 설명 문자열을 직접 든 enum.
- 정상 호출자가 마주칠 전파 예외는 @throws로 적는다. 선행조건 위반으로만 나는 예외(버그 백스톱)는
  계약이 아니라 적지 않는다. @return과 자명한 @param은 두지 않는다.
- 형식: 타입·메서드 요약은 3인칭 서술형(`사용자를 등록한다`), 필드·enum 상수 요약은 명사구
  (`최초 등록 시각`). 마침표로 끝낸다. 계약은 /** */, 구현의 "왜"는 //.
- 라인 주석은 비자명한 "왜"만. 코드가 말하는 What 되풀이 금지. 예외는 조율 메서드의 단계 표시
  (// 1. 입력 검증)로 단계가 셋 이상이고 순서 자체가 정보일 때만.
- 금지: 결정 내러티브, 검사가 지키는 것 되풀이, 주석 처리된 코드, 작성자·날짜.

참조 구현(이미 이 규칙으로 정리됨 — 톤을 맞춘다):
- module-domains/domain-order 전체, module-apps/app-api/.../facade/ 전체.
- 정적 팩토리 요약의 기준: OrderInfo.from, CartInfo.from, PaginationResponse.from.

스캔 대상(9파일 전수):
- module-external/external-payment — FakePaymentGateway, FakeGatewayTimeoutException, package-info
- module-infra/infra-messaging — InProcessMessagePublisher, package-info
- module-infra/infra-redis — RedisIdempotencyStore, RedisLoginRateLimitStore, SchedulerLockConfig,
  package-info

관측된 발산(반드시 포함하되 여기서 멈추지 말 것). 괄호는 확인된 소유처다 — 착수 시 직접 재확인한다:
1) FakePaymentGateway 타입 doc — 트리거 금액 분기 3종 <ul>, 인메모리 보관의 재시작 한계 수용.
   (REQUIREMENTS.md:153,159,163 / DOMAIN_MODEL.md:554)
2) RedisIdempotencyStore 타입 doc — SET NX 원자 선점, TTL 만료, fail-closed 채택 근거.
   (REQUIREMENTS.md:172)
3) RedisLoginRateLimitStore 타입 doc — INCR+PEXPIRE를 한 Lua로 묶은 근거, fail-closed.
   (REQUIREMENTS.md:173)
4) SchedulerLockConfig 타입 doc — SET NX 선점·TTL 메커니즘. 마지막 줄이 "Redis 채택 근거는
   REQUIREMENTS.md 제약·전제가 소유한다"고 스스로 밝히면서 앞 2줄을 더 쓴다. (REQUIREMENTS.md:166)
5) InProcessMessagePublisher 타입 doc — AFTER_COMMIT 통지 시점, 무손실·내구성 보장 없음.
   (REQUIREMENTS.md:6 / DOMAIN_MODEL.md:11)
6) RedisIdempotencyStore의 상수 라인 주석 3건 — in-flight 수명 선택 근거는 국소적 "왜"라 남길
   후보지만, KEY_PREFIX 주석은 코드가 말하는 What이다. 개별 판정한다.

판정 지침:
- 이 모듈들의 오버라이드 구현(approve·cancel·inquire·publish·tryBegin·complete·
  incrementAndCount)은 상위 포트 계약을 그대로 따르므로 요약 면제다.
- 면제가 성립하려면 상위 포트(PaymentGateway·MessagePublisher·IdempotencyStore·
  LoginRateLimitStore) doc이 계약을 담고 있어야 한다. 담고 있는지 확인하고, 담지 못하면 상위를
  고치는 편이 맞다는 판단과 함께 보고한다(상위는 이미 정리된 슬라이스 소관이므로 임의로 고치지 않고
  보고만 한다).

삭제 규약(중요): 타입 doc에서 설계 근거·메커니즘 서술을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·
REQUIREMENTS.md·해당 메서드 doc 중 어디가 소유하는지 grep으로 확인하고 파일·행 근거를 보고에 적는다.
소유처가 없으면 지우지 말고 적절한 위치(메서드 doc·라인 주석)로 옮긴다.

완료 기준:
- 대상 9파일 전수 스캔. 요약 의무 4종 누락 0, 면제 대상에 요약을 단 곳 0.
- 타입 doc에 전이 규칙·동시성 메커니즘·설계 근거 잔존 0.
- 형식 준수(타입·메서드=서술형, 필드·enum 상수=명사구).
- 주석 용어가 DOMAIN_MODEL.md 용어집과 일치. 미등재 개념을 썼으면 용어집 등재를 커밋에 포함한다.
- 삭제분마다 소유처 확인 근거(파일·행)를 보고에 포함. 소유처 확인 없이 삭제한 건 0.
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 항목별 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + docs/architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 2. app-api web 계층 (컨트롤러 + web DTO + auth 마커)

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"을 app-api의 web 계층에 적용한다.
이전 슬라이스가 컨트롤러 15개(@Operation)와 web DTO 49개(@Schema)를 통째로 "면제 대상 = 스캔만"으로
두어, 애노테이션이 소유하지 않는 축이 정리되지 않은 채 남았다. 동작·시그니처는 바꾸지 않는다 —
Javadoc·주석만 손댄다.

규칙 정본은 위 문서다. 반드시 전문을 로딩한다. 이 마이그레이션은 이전 규칙과 방향이 반대인 항목이
있으니 기억으로 적용하지 말 것. 요지:
- 주석은 한국어. 용어는 DOMAIN_MODEL.md 도메인 용어집에 등재된 것만 쓴다.
- 요약은 타입·메서드·영속 필드·enum 상수에 둔다(각 한 문장).
  · 타입 = 그 타입이 무엇인지만. 상태 전이 규칙·동시성 메커니즘·설계 근거는 적지 않는다.
  · 메서드 = 공개면 호출자 계약, 내부면 그 메서드가 맡은 일.
- 요약을 두지 않는 자리: 오버라이드 / 필드 반환 한 줄 접근자 / 생성자 / 파생 쿼리 메서드 /
  테스트 메서드 / 설명 문자열 보유 enum / API 계약을 @Operation·@Schema가 소유하는 타입·멤버.
- Javadoc은 문서화 대상 자신의 계약만 적는다. 협력자의 내부 동작·다른 요소의 계약은 되풀이하지
  않는다. 관측 가능한 계약(전파 예외·외부 효과)은 협력자를 거쳐 실현돼도 적는다.
- 형식: 타입·메서드 요약은 3인칭 서술형, 마침표로 끝낸다. 계약은 /** */, 구현의 "왜"는 //.
- 금지: 결정 내러티브, 검사가 지키는 것 되풀이, 주석 처리된 코드, 작성자·날짜.

면제의 정확한 경계 — 이 슬라이스의 핵심이다. 오해하면 방향이 반대로 간다:
- @Operation·@Schema가 소유하는 것은 API 계약이다. 핸들러 메서드 요약과 record 컴포넌트의 @param이
  여기 걸린다. 이 자리에 요약을 새로 다는 것은 규칙 위반이다.
- 면제가 걸리지 않는 자리(정리 대상):
  · 타입 doc의 금지 사항 — 전이 규칙·동시성 메커니즘·설계 근거는 애노테이션 유무와 무관하게 금지다.
  · 컨트롤러의 private 헬퍼 메서드.
  · request DTO의 toXxx() 변환 메서드.
  · response DTO의 from(XInfo) 정적 팩토리.

정적 팩토리 판정(확정 — 뒤집지 말 것): response DTO의 from(...) 27곳에 한 문장 요약을 단다.
- 근거 1: 규칙이 생성자 면제 사유를 "타입 doc·정적 팩토리 doc. 생성 계약은 팩토리가 소유한다"로
  적는다. 팩토리는 면제의 반대편, 곧 계약의 소유처다.
- 근거 2: 완료된 슬라이스가 도메인 Info 12곳과 common-web PaginationResponse.from에 전부 요약을
  달았다. web response DTO만 비어 있는 것이 발산이다.
- 톤: 참조 구현이 `/** 주문 엔티티에서 조회 모델을 만든다. */`이므로 web에서는
  `/** 주문 조회 모델에서 응답을 만든다. */` 수준의 한 문장. 장식하지 않는다.

스캔 대상(67파일 전수): module-apps/app-api/src/main/java/com/commerce/api/web/ 하위 전부
- web/auth/ 3파일(Admin·Anonymous·Authenticated) — 이미 현재 규칙으로 정리됐다(커밋 06ac2dc).
  확인만 하고 규칙에 맞으면 손대지 않는다.
- web/v1/ 64파일 — 컨트롤러 15, request/response record 49.

관측된 발산(반드시 포함하되 여기서 멈추지 말 것):
1) PaymentWebhookController 타입 doc 7줄 — HMAC-SHA256 서명 검증 방식·상수 시간 비교·401 거부·
   중복 전달 무해가 전부 문서 소유다(REQUIREMENTS.md:55,135,168 / DOMAIN_MODEL.md:659). 이전
   계획(plan.md)이 1차 규칙 아래 "무변경"으로 판정했으나 타입 doc 축약 규칙으로 재판정 대상이다.
   이 컨트롤러가 토큰 인증 표면 밖이라는 사실만은 애노테이션이 담지 못하는 자기 계약일 수 있으니
   개별 판정하고 근거를 적는다.
2) 컨트롤러 타입 doc의 <p> 정책 서술 6건 — CouponAdminController(중지 비소급),
   MemberAdminController·MemberController(정지·탈퇴 독립 축), ProductAdminController(편집 스냅샷
   무영향·논리삭제 비연쇄), ProductVariantAdminController(가격 변경 스냅샷 무영향),
   StockAdminController(변형당 1행), ProductController(전부 공개).
   일괄 삭제도 일괄 존치도 아니다. 각각 DOMAIN_MODEL.md·REQUIREMENTS.md 소유 여부를 grep으로
   확인해 개별 판정하고, 존치하면 "애노테이션이 담지 못하는 자기 계약"인 근거를 적는다.
3) DTO 타입 doc이 협력자 계약을 되풀이하는 건 — LoginRequest(401 코드·계정 존재 비노출),
   MemberRegistrationRequest(도메인 에러코드 2종), AddCartItemRequest·ChangeCartItemQuantityRequest·
   CheckoutRequest(토큰 주체 도출 = 컨트롤러의 인증 계약), DiscountRequest(형별 조합 검증은
   도메인 소유).
4) PaymentWebhookController의 private 헬퍼 3개(requireValidSignature·sign·parse) — 요약 없음.
5) response DTO from(...) 27곳 — 요약 없음(위 확정 판정 적용).

이 슬라이스에서 건드리지 말 것:
- 용어 발산: "발급 쿠폰"(용어집 등재어는 "발급분")과 "논리삭제"(등재어는 "소프트삭제")가 web 표면에
  다수 있다. 이는 todo.md 슬라이스 3의 결정 대기 대상이라 전역 sweep으로 한 번에 처리한다.
  표현을 바꾸지 말고 관측 사실만 보고한다.
- @Schema description은 API 문서 계약이라 지우지 않는다. 중복 제거는 Javadoc 쪽에서 한다.

삭제 규약(중요): 타입 doc에서 설계 근거·정책 서술을 잘라내기 전에 그 내용을 DOMAIN_MODEL.md·
REQUIREMENTS.md·애노테이션·해당 메서드 doc 중 어디가 소유하는지 grep으로 확인하고 파일·행 근거를
보고에 적는다. 소유처가 없으면 지우지 말고 적절한 위치로 옮긴다.

완료 기준:
- 대상 67파일 전수 스캔.
- @Operation·@Schema가 소유하는 자리에 요약을 새로 단 곳 0.
- from(...) 27곳·toXxx() 6곳·private 헬퍼 3곳에 요약 누락 0.
- 타입 doc에 전이 규칙·동시성 메커니즘·설계 근거 잔존 0.
- 컨트롤러·DTO 타입 doc의 잔존 서술마다 존치 근거 또는 삭제 소유처가 보고에 있다.
- 용어 표현 변경 0(슬라이스 3 소관).
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인용 — 주석 규칙 준수 증명 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 파일별 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + docs/architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 3. 소프트삭제·발급분 용어 전역 정합

`상태: 보류(결정 대기)`다. `todo.md`의 "선행 결정 필요"에서 안 A·안 B 중 하나가 확정되기 전에는 착수 프롬프트를 쓰지 않는다 — 결정 내용이 프롬프트의 작업 지시 자체이기 때문이다. 결정이 나면 이 자리에 코드블록을 채운다.

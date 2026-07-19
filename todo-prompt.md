# TODO 착수 프롬프트

`todo.md`의 각 슬라이스를 새 세션에서 착수할 때 그대로 붙여넣는 프롬프트다. 번호는 `todo.md`와 일치한다. 위에서부터 진행한다.

각 프롬프트는 자립적이도록 공통 규칙·규칙 요지를 앞에 담는다(서브에이전트는 이 세션 대화를 보지 못한다). 아래 코드블록만 복사해 붙여넣으면 된다.

- 규칙 정본은 `docs/coding-conventions.md`의 "### 코드 주석·Javadoc"이다 — 착수 시 반드시 로딩한다.
- 변경은 동작·시그니처 무변경(Javadoc·주석만). 규칙에 이미 맞는 주석은 개선·재작성하지 않는다(외과적 편집).
- 한 슬라이스가 끝나면 `todo.md`에서 해당 항목을 완료 처리하고 다음 번호로 넘어간다.

---

### 1. `@throws` 파리티 — 도메인 예외·낙관락 충돌 문서화

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위·판정 기준을 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"의 @throws 규칙을 코드에 정합시킨다.
동작·시그니처는 바꾸지 않는다 — Javadoc만 손댄다.

규칙(정본은 위 문서, 요지):
- "정상 호출자도 마주칠 수 있는 전파 예외(거부·낙관락 충돌 등)는 @throws로 적는다. 호출자 보장
  선행조건 위반으로만 나는 예외(버그 백스톱)는 계약이 아니라 적지 않는다."
- 즉 도메인 예외(BaseException 하위 = {Name}Exception, 거부 모드)나 낙관락 충돌(@Version 애그리거트의
  ObjectOptimisticLockingFailureException→409)을 호출자에게 전파하면 @throws로 문서화한다. 협력자를
  거쳐 전파돼도 대상의 관측 계약이면 적는다.
- 선행조건 IllegalArgumentException(호출자 보장 위반 = 버그 백스톱, 전파되면 500)은 @throws 대상이
  아니다. 예외 분류 기준(도메인 예외 vs IAE)은 같은 문서 "### 타입 선언"의 커스텀 예외 규칙이 소유한다.

스캔 대상(전수 — 관측 예시에 그치지 말 것):
- 엔티티(@Entity) 상태전이 메서드·정적 팩토리, 도메인 서비스(Reader/Appender/Modifier/Processor/
  Validator) 진입 메서드, 값 객체 연산, 리포지토리 @Query·파생 메서드의 public·protected 멤버 전부.
- 관측된 누락(반드시 포함하되 여기서 멈추지 말 것): Stock.markSoldOut·markSellable·discontinue,
  StockModifier의 위 위임 메서드, Payment.approve·fail(도메인 예외를 던지나 @throws 누락).
- 관측된 과문서화: Money.minus의 @throws IllegalArgumentException(선행조건 IAE) — 제거.

완료 기준:
- 도메인 예외·낙관락 충돌을 정상 호출자에게 전파하는 모든 public·protected 메서드가 그 예외를
  @throws로 적는다(누락 0). 선행조건 IAE를 @throws로 적은 곳 0(오문서화 0).
- 각 메서드가 실제로 어떤 예외를 어떤 조건에 던지는지 파일에서 확인하고 @throws 사유를 적는다(추측 금지).
- 표기는 규칙대로 {@code}/{@link}. @return·자명한 @param은 새로 넣지 않는다.
- 동작·시그니처 무변경. ./gradlew build green(회귀·포맷 확인 — 주석 규칙 준수 증명은 아님).
- 독립 리뷰(구현과 분리된 서브에이전트)가 스캔 대상 전반에서 누락 0·오문서화 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + architecture.md "빌드가 강제하는 불변식" 자기검증 후 보고한다.
```

### 2. Swagger 단일 출처 중복 제거

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 스캔 범위를 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"의 Swagger 단일 출처 규칙을 코드에 정합시킨다.
동작은 바꾸지 않는다 — Javadoc만 손댄다.

규칙(정본은 위 문서, 요지):
- "컨트롤러 엔드포인트·DTO처럼 Swagger 애노테이션(@Operation·@Schema)이 API 계약을 소유하면,
  애노테이션을 단일 출처로 삼고 같은 내용을 Javadoc으로 중복하지 않는다. 애노테이션에 없는 계약만
  Javadoc에 남긴다."
- 재사용 타입은 자기 계약만 적는다(도메인 Info와 web Response는 별개 타입·별개 계층).

스캔 대상(전수):
- module-apps/app-api의 web/v1 request·response record 전부, 컨트롤러 클래스·메서드 Javadoc.
- 관측된 중복(포함하되 멈추지 말 것): CheckoutRequest가 클래스 Javadoc + 클래스 @Schema를 둘 다 두고
  첫 문장이 겹친다.
- 도메인 Info↔web Response 근접 중복(예: OrderInfo Javadoc ↔ OrderResponse @Schema).

완료 기준:
- web Request/Response DTO·컨트롤러에서 @Schema/@Operation과 내용이 겹치는 Javadoc을 제거한다.
  애노테이션에 담지 못하는 계약(교차필드 규칙 등 — 예: CheckoutRequest의 "결제 수단은 0원이면 생략")은
  한 곳에만 남긴다(@Schema로 옮기거나 Javadoc에 유지하되 중복 금지). 소비자에게 무의미한 설계
  내러티브는 넣지 않는다(규칙 "결정 내러티브 금지").
- @Schema description은 API 문서 계약이라 지우지 않는다 — 중복 제거는 Javadoc 쪽에서 한다.
- 도메인 Info↔web Response는 각 타입이 자기 계층 관점만 서술하게 한다(Info=경계 조회 모델 역할,
  Response=API 응답 형태). 서로 복붙 동일 문장을 남기지 않는다.
- 동작 무변경. ./gradlew build green.
- 독립 리뷰(구현과 분리된 서브에이전트)가 web DTO·컨트롤러에서 애노테이션-Javadoc 중복 0을 확인한다.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

### 3. 잔여 규칙 정합 sweep

```
이 저장소(spring-boot-commerce)의 개발 규칙은 AGENTS.md·docs/가 소유한다. 먼저 로딩해 준수하고,
구현 전 카테고리별 스캔 범위를 밝힌 뒤, 슬라이스 머지 전 독립 리뷰를 수행한다.

작업: docs/coding-conventions.md "### 코드 주석·Javadoc"의 나머지 규칙 발산을 정리한다(슬라이스 1·2가
처리한 @throws·Swagger 밖의 잔여). 동작은 바꾸지 않는다 — Javadoc·주석만 손댄다. 규칙에 이미 맞는
주석은 개선·재작성하지 않는다(외과적 편집).

카테고리(각각 전수 스캔 후 발산만 정정):
1) 자기서술 요약: 공개(public·protected) 메서드가 이름·반환 타입 외에 전파 예외·부수효과·경계동작이
   없는데도 이름을 되풀이하는 요약을 달았으면 제거한다. 반대로 계약이 있는데 요약이 없으면 추가한다.
2) 형식: 호출자 계약이 라인 주석(//)으로 적혔거나, 구현의 "왜"가 불필요하게 Javadoc 블록으로 적힌 곳을
   규칙(계약→/** */, 왜→//)에 맞춘다.
3) 태그 규율: @return과 자명한 @param을 제거한다(app-api·web 포함 전수 — 도메인 메인은 @return 0건이나
   다른 모듈 확인). @param은 record 컴포넌트나 비자명한 제약(조건부 존재·범위)에만 남긴다.
4) 인라인 일관성: 동종 @Query 정렬 근거 주석을 일관되게 둔다. 관측: OrderRepository의 @Query 정렬에
   ProductRepository·CouponRepository에 있는 "UUIDv7이 시간순이라 id desc가 최신순" 근거 주석이 없다.

완료 기준:
- 위 4개 카테고리 전수 스캔 후 발산 0.
- 동작·시그니처 무변경. ./gradlew build green.
- 독립 리뷰(구현과 분리된 서브에이전트)가 카테고리별 잔여 0을 전수 확인한다.
- 완료 후 ./gradlew build 통과 + 자기검증 후 보고한다.
```

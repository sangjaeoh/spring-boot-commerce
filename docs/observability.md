# Observability

## 언제

- 로그를 남기는 코드를 작성하거나 로그 레벨을 정할 때.
- 상관 ID·MDC를 다룰 때.
- 메트릭을 계측하거나 이름·태그를 정할 때.
- actuator 노출·헬스 그룹을 구성할 때.

## 규칙

### 로깅

- 로그는 SLF4J API로만 남긴다.
- logback API를 직접 호출하지 않는다.
- 로거 선언 형식은 → [coding-conventions](coding-conventions.md)의 접근제한자.
- 운영 프로파일은 콘솔 JSON 구조화 로깅을 사용한다.
  - `logging.structured.format.console: ecs`로 구성한다.
  - Spring Boot 내장 기능만 사용하고 별도 인코더 의존성을 추가하지 않는다.
  - MDC는 구조화 출력에 자동 포함된다.
- 로컬 프로파일은 텍스트 출력을 유지한다.
- 로그 메시지는 한국어 사실 서술로 작성한다.
- 도메인 용어는 용어집 표준어를 사용한다(→ [coding-conventions](coding-conventions.md)의 네이밍).
- 로그에 싣는 값은 엔티티 ID·상태값·건수·소요 시간으로 한정한다.
- 자유 텍스트 필드(제목·본문·이메일)와 요청·응답 페이로드 전문은 싣지 않는다.
- 비밀값(토큰·비밀번호·키)과 개인정보는 로깅하지 않는다.
- 경계 도달 예외는 경계의 전역 핸들러가 한 번만 로깅한다.
- 중간 계층은 예외를 로깅한 뒤 다시 던지지 않는다.
- 전역 핸들러는 미분류 5xx만 ERROR로 남긴다.
- 도메인 예외의 4xx 응답은 로깅하지 않는다.

### 상관 ID

- common-web `RequestIdFilter`가 요청 상관 ID를 소유한다.
  - `X-Request-Id` 요청 헤더가 있으면 수용하고 없으면 생성한다.
  - MDC 키 `requestId`로 싣는다.
  - 응답 헤더로 반환한다.
  - MDC 제거는 필터가 finally에서 수행한다.
- 비요청 경로는 진입점이 상관 키를 싣고 제거한다.
  - 이벤트 소비·아웃박스 재전달은 이벤트 식별자를 MDC 키 `eventId`로 싣는다.
  - 스케줄·배치 작업은 작업 식별자를 MDC 키 `jobId`로 싣는다.
- MDC는 스레드 로컬 기반이라 실행자 경계에서 전파되지 않는다.
- 태스크를 다른 실행자로 넘기는 쪽이 MDC를 명시 전파한다.
- 전파는 MDC 스냅샷을 복사·복원하는 `TaskDecorator`로 한다.

### 로그 레벨

- ERROR는 사람 개입이 필요한 실패에만 쓴다.
- ERROR는 알림 대상 기준이다.
- WARN은 자동 복구·강등된 이상에 쓴다(캐시 get 오류의 미스 강등 등).
- INFO 허용 지점은 쓰기 서비스의 상태 변경 완료 지점, 배치·이벤트 소비 작업의 완료 요약, 앱 기동 구성 요약뿐이다.
- 그 외 지점에 INFO를 남기지 않는다.
- DEBUG는 개발 진단 전용이다.
- 운영 기본 레벨은 INFO다.

### 메트릭

- 계측은 Micrometer로 한다.
- 메트릭 이름은 점 구분 소문자로 짓는다.
- 메트릭 이름의 마지막 세그먼트는 측정 대상을 가리킨다(예: `cache.evict.errors`).
- 메트릭 이름은 상수로 선언하고 계측을 소유한 모듈에 둔다.
- 태그 값은 enum 또는 컴파일 타임 상수만 쓴다.
- 무한 카디널리티 값(엔티티 ID·사용자 ID·URL 경로 변수·자유 텍스트)은 태그로 싣지 않는다.
- 도메인·query 모듈은 Micrometer에 의존하지 않는다.
- 비즈니스 메트릭 계측은 관찰 용도 이벤트 구독 또는 앱 계층이 소유한다 → [architecture](architecture.md)의 크로스 도메인 상호작용.
- 캐시 통계는 저장소 통계 활성으로 충족한다 → [caching](caching.md)의 구성.
- 캐시 통계를 별도로 계측하지 않는다.

### actuator

- 노출 endpoint는 `health`·`info`·`prometheus`로 한정한다.
- 관리 endpoint는 별도 관리 포트(`management.server.port`)로 분리하고 외부에 노출하지 않는다.
- readiness·liveness 프로브 그룹을 활성화한다(`management.endpoint.health.probes.enabled: true`).
- 프로브 노출 포트(관리 포트 단독 또는 `add-additional-paths` 메인 병행)를 구성에 명시한다.
- 캐시 전용 저장소의 헬스 인디케이터는 readiness에서 제외한다 → [caching](caching.md)의 장애.

### 강제와 리뷰

- 아래는 아키텍처 테스트가 빌드에서 강제한다.
  - 전 모듈의 `ch.qos.logback..` 임포트 금지(SLF4J 경유).
  - 도메인·query 모듈의 `io.micrometer..` 임포트 금지.
  - 표준 스트림 접근(`System.out`·`printStackTrace`) 금지(규칙 소유 → [coding-conventions](coding-conventions.md)의 접근제한자).
- 강제 항목의 아키텍처 테스트가 없으면 최초 도입과 같은 변경에서 추가한다 → [architecture](architecture.md)의 아키텍처 테스트 모듈.
- 아래는 리뷰로 검증한다.
  - 로그 레벨 배정의 허용 지점 준수.
  - 로그 값 한정 목록 준수(비밀값·개인정보·페이로드 부재).
  - 메트릭 태그 카디널리티.
  - actuator 노출·프로브 구성.

### 범위 밖

- 분산 트레이싱(Micrometer Tracing·OpenTelemetry): 단일 프로세스는 MDC 상관 ID로 추적한다.
- 분산 트레이싱 도입은 모듈 추출 시 재평가한다.
- 로그 수집·저장(Loki·ELK)·대시보드·알림 규칙: 서버 구성이 소유한다.

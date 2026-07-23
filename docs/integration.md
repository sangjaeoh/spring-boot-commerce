# Integration

## 언제

- 외부 시스템을 HTTP로 호출하는 코드를 작성할 때.
- `Gateway` 계약·구현을 만들거나 수정할 때.
- 외부 호출의 timeout·재시도·오류 처리를 정할 때.

## 규칙

### 계약과 배치

- 외부 시스템 호출은 계약(도메인 required 또는 common 소유 기술 계약) 뒤에만 둔다 → [architecture](architecture.md)의 구현 배치 판정.
- 계약 접미사는 → [coding-conventions](coding-conventions.md)의 클래스 접미사.
- 도메인 계약 구현의 배치는 → [architecture](architecture.md)의 도메인 모듈 구조를 따른다.
- 기술 계약 구현의 배치는 → [architecture](architecture.md)의 infra 모듈 구조를 따른다.
- 외부 API 요청·응답 DTO는 구현 소유 모듈(`adapter/integration` 또는 infra)에 둔다.
- 외부 API DTO는 모듈 밖에 노출하지 않는다.
- 외부 타입은 경계에서 도메인 값으로 변환한다.

### 클라이언트

- HTTP 클라이언트는 `RestClient`만 사용한다.
- `WebClient`·`RestTemplate`·서드파티 HTTP 클라이언트는 도입하지 않는다.
- `RestClient`는 자동 구성된 `RestClient.Builder`를 주입받아 만든다.
- 정적 `RestClient.create()`·`RestClient.builder()`를 호출하지 않는다.
- 어댑터 전용 클라이언트 설정 빈의 배치는 → [architecture](architecture.md)의 도메인 모듈 구조.

### timeout

- 연결·응답 timeout은 명시한다.
- 라이브러리 기본값에 맡기지 않는다.
- 전역 기본값은 앱 구성 `spring.http.clients.*`가 소유한다.
- 연동별로 다른 값은 그 어댑터의 클라이언트 설정 빈이 소유한다.
- 응답 timeout 상한은 5초다.
- 상한 초과 값의 채택은 사람 합의를 거친 예외로만 한다.

### 재시도

- 재시도는 Spring Framework 코어 `@Retryable`(`org.springframework.resilience.annotation`)·`RetryTemplate`(`org.springframework.core.retry`)로 한다.
- spring-retry 의존성을 추가하지 않는다.
- `@Retryable`을 쓰는 도메인을 임베드하는 앱은 `@EnableResilientMethods` 구성을 동반한다.
- 재시도는 멱등 호출에만 허용한다.
- 횟수 상한과 백오프를 명시한다.
- 비멱등 쓰기 호출은 재시도하지 않는다.
- 전달 보장이 필요한 외부 쓰기는 아웃박스 재전달로 푼다(전달 보장 계약은 → [architecture](architecture.md)의 크로스 도메인 상호작용).
- 아웃박스 재전달은 재실행이므로 외부 쓰기에는 멱등 키(외부 요청 식별자)를 함께 전달한다.

### 오류 변환

- 외부 호출 실패는 adapter 구현이 도메인 예외 또는 실패 값으로 변환한다.
- 클라이언트 스택 예외(`RestClientException` 등)를 도메인에 전파하지 않는다.
- 실패 분류(재시도 가능 여부·호출자 통지 여부)는 required 계약이 소유한다.

### 서킷브레이커

- 서킷브레이커는 기본 미도입이다.
- 짧은 timeout이 1차 방어선이다.
- 채택은 외부 의존 규모·장애 이력을 근거로 사람 합의를 거친 예외로만 한다.

### 테스트

- 도메인 테스트는 required 계약 목으로 검증한다 → [testing](testing.md)의 목·스텁.
- adapter 구현 테스트는 `@RestClientTest` + `MockRestServiceServer`로 검증한다 → [testing](testing.md)의 도구.

### 강제와 리뷰

- 아래는 아키텍처 테스트가 빌드에서 강제한다.
  - `org.springframework.web.client..` 임포트는 도메인 `adapter` 구역과 infra 모듈만 허용.
  - `RestClient` 정적 팩토리(`create`·`builder`) 호출 금지.
- 아래는 컨벤션 플러그인이 강제한다.
  - `spring-webflux` 금지 의존성 등록(`WebClient` 차단, 규칙 소유 → [code-quality](code-quality.md)).
- 강제 항목의 장치가 없으면 최초 도입과 같은 변경에서 추가한다 → [architecture](architecture.md)의 아키텍처 테스트 모듈.
- 아래는 리뷰로 검증한다.
  - timeout 값의 상한 준수.
  - 재시도의 멱등성 판정.
  - 재시도 활성 구성(`@EnableResilientMethods`)의 동반.
  - 오류 변환의 완전성(외부 타입 유출 부재).

### 범위 밖

- 메시지 브로커 연동·웹훅 수신·파일 스토리지: 실물 필요 시 추가한다.
- 서킷브레이커·벌크헤드·rate limiter 구체 규칙: 채택 합의 시 작성한다.

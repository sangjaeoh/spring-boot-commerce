# Coding Conventions

## 언제

- 새 타입을 선언할 때.
- 객체 생성 진입점과 변환 방향을 설계할 때.
- 접근제한자를 결정할 때.
- 타입·메서드·필드 이름을 지을 때.
- 코드 주석과 Javadoc을 작성할 때.

## 규칙

### 타입 선언

#### 타입 종류

- 타입은 역할에 따라 아래 표대로 선언한다.

| 대상 | 클래스 | 비고 |
|---|---|---|
| JPA 엔티티 | `class` | 명시적 getter, `protected` 기본 생성자, setter 금지 |
| Info(경계 조회 모델) | `record` | `from(entity)` 정적 팩토리 |
| 도메인 이벤트 | `record` | 불변 페이로드 |
| request/response DTO | `record` | Bean Validation은 request만 적용 |
| 값 객체 | `record` | 값 기반 동등성 |
| ErrorCode | `enum` | `ErrorCode` 구현 |
| 커스텀 예외 | `class` | 경계 도달 예외만 `BaseException` 상속 |
| 서비스·파사드·설정 | `class` | `final` 필드 + 생성자 주입 |
| 제공(provided)·요구(required) 계약 | `interface` | 소유·배치는 → [architecture](architecture.md) |

#### 예외 규칙

- `BaseException`은 경계까지 전파되는 예외만 상속한다.
- 국소 처리 예외는 JDK 관용 예외를 사용한다.
- 외부 입력으로 도달 가능한 불변식 위반은 도메인 예외로 처리한다.
- 호출자 버그로만 깨지는 선행조건은 `IllegalArgumentException`으로 처리한다.
- `BaseException`은 `BaseException(ErrorCode)` 생성자만 노출한다.
- 도메인별 `{Name}ErrorCode` enum은 `ErrorCode` 인터페이스를 구현한다.
- 경계 도달 예외의 HTTP 상태는 `ErrorCode`가 소유한다.

#### 접근제한자

- 기본은 가장 좁은 가시성으로 선언한다.
- 도메인 내부 전용은 package-private을 사용한다.
- public은 경계 계약일 때만 사용한다.
- 필드는 모두 `private`로 선언한다.
- 빈 필드는 `private final`로 선언한다.
- `protected` 필드는 금지한다.
- 로거는 `private static final`로 선언한다.
- `System.out`, `printStackTrace`는 금지한다.
- 테스트를 위해 접근제한자를 완화하지 않는다.

### 객체 생성·변환

#### 객체 생성

- 엔티티 생성 진입점은 정적 팩토리 `create(...)` 하나만 둔다.
- public 생성자를 두지 않는다.
- JPA 기본 생성자는 `protected`로 둔다.
- 팩토리 네이밍은 아래 규칙을 따른다.
  - `create(...)`: 새 엔티티 생성
  - `of(...)`: 값 조합
- `record` 검증은 compact constructor에서 수행한다.
- 검증 실패는 생성 시점에 예외를 던진다.
- Info·DTO·VO의 컬렉션 필드는 방어적 복사 후 불변화한다.
- JPA가 관리하는 엔티티 매핑 컬렉션은 이 규칙의 대상이 아니다.

#### 객체 변환

- 변환은 대상 타입의 정적 팩토리에 둔다.
- `from(source)`는 단일 원본 변환에만 사용한다.
- `*Mapper`, `*Converter` 매핑 클래스는 만들지 않는다.
- 예외: JPA `AttributeConverter`는 허용한다.
- 변환 방향은 바깥이 안을 아는 방향만 허용한다.
- 역방향 변환(`Info.toEntity()`)은 금지한다.

### 네이밍

#### 엔티티·테이블

- 엔티티는 단수 명사를 사용한다.
- 테이블명은 snake_case 단수형을 사용한다.
- 표준과 다른 테이블명은 도메인 용어집에 등록된 경우만 허용한다.
- 용어집에 없는 이탈은 임의로 만들지 않는다.
- 도메인 용어집은 `docs/`의 단일 문서가 소유한다.

#### 서비스 접미사

| 접미사 | 역할 |
|---|---|
| `Reader` | 조회 |
| `Appender` | 생성 |
| `Modifier` | 수정 |
| `Remover` | 삭제 |
| `Processor` | 외부 부수효과·복합 상태 전이 조율 |
| `Validator` | 검증 |

- 표에 없는 접미사는 도입 전에 사람 승인을 받는다.
- 역할 접미사 이름은 provided 계약 인터페이스가 소유한다. 구현 서비스는 `Default{계약명}`으로 짓는다.
- 계약 없는 내부 협력 서비스는 역할 접미사 이름을 그대로 쓴다.

#### 메서드 네이밍

| 작업 | 리포지토리 | 도메인 서비스 | 엔티티 |
|---|---|---|---|
| 조회 | `find*`, `get*`, `exists*`, `count*` | `get*`, `find*` → `Info` 반환 | — |
| 생성 | `save` | 도메인 의도 동사 → ID 반환 | `create(...)` |
| 수정 | dirty checking | 도메인 의도 동사 | 의도 동사 메서드 |

- 소프트삭제 조회는 삭제 미포함이 기본이다.
- 삭제 포함 조회만 별도 이름을 사용한다.
- 활성-only 조회는 이름에 `DeletedAtIsNull`을 담고, 삭제 포함 조회는 `IncludingDeleted`를 붙인다.
- boolean 메서드는 `is*`, `has*`, `can*`를 사용한다.
- `update`, `set`, `change` 같은 범용 동사는 금지한다.
- 하드삭제는 명시 동사(`purge*`, `anonymize*`)로만 노출한다.

#### 클래스 접미사

| 접미사 | 대상 |
|---|---|
| `Controller` | 컨트롤러 |
| `Facade` | 파사드 |
| `Repository` | 리포지토리 |
| `Info` | 경계 조회 모델 |
| `View` | 파사드 합성 뷰 |
| `Request` / `Response` | DTO |
| 단수 명사 | 엔티티·값 객체 |
| 과거형 | 도메인 이벤트 |
| `Status` | 상태 enum |
| `Reason` | 사유 enum |
| `ErrorCode` | 에러 코드 enum |
| `Exception` | 커스텀 예외 |
| `Gateway` / `Store` / `Publisher` | required 계약(외부 시스템·영속) |
| `Filter` | 서블릿 필터 |
| `Listener` | 이벤트 리스너 |
| `Config` | 설정 |
| `Aspect` | AOP |
| `Converter` | JPA `AttributeConverter` |
| `Fixture` | 테스트 픽스처 |

### 코드 주석·Javadoc

#### 공통 규칙

- 주석은 한국어로 작성한다.
- 도메인 용어는 용어집에 등록된 한국어 용어만 사용한다.
- 타입 doc에서 처음 등장할 때만 `한국어(영문식별자)`로 병기한다.
- Javadoc은 API 계약만 기술한다.
- 구현 방식(How)은 기술하지 않는다.
- 문서화 대상 자신의 계약만 기술한다.
- 코드·값은 `{@code ...}`를 사용한다.
- 타입은 `{@link ...}`를 사용한다.
- 자명한 `@param`, `@return`은 생략한다.

#### 요약 규칙

- 클래스 요약은 “무엇인지”만 작성한다.
- 이름만으로 역할이 명확하면 클래스 주석을 생략할 수 있다.
- 메서드 요약은 호출자 계약 또는 내부 책임만 작성한다.
- 정상 호출자가 마주칠 전파 예외만 `@throws`에 작성한다.
- 선행조건 위반으로만 발생하는 예외는 문서화하지 않는다.
- 필드를 그대로 반환하는 getter만 주석을 생략한다.
- 계산·파생·방어적 복사가 있는 getter는 주석을 작성한다.
- 필드 요약은 `@Entity`, `@Embeddable`의 영속 필드에 적용한다.
- 필드 요약은 값의 의미만 작성한다.
- 단위·범위·시간대·존재 조건을 명시한다.
- 동작 효과나 상태 변화는 메서드 문서에 작성한다.
- 컬럼 코멘트로 대체하지 않는다.
- enum 상수는 의미와 진입 조건만 작성한다.
- `@Embeddable record`는 모든 컴포넌트에 `@param`을 작성한다.
- 일반 record는 파생·합성 컴포넌트만 문서화한다.

#### 라인 주석

- 라인 주석은 단계 표시와 이유 설명만 작성한다.
- 단계는 `// 1. 입력 검증` 형식을 사용한다.
- 단계명은 명사구 한 줄로 작성한다.
- 단계가 두 개 이상일 때만 번호를 사용한다.
- 루프 내부에 중첩 번호를 두지 않는다.
- 코드만으로 알 수 없는 이유만 설명한다.
- 이름이 이미 설명하는 내용은 반복하지 않는다.
- 주석 없이 읽기 어려우면 메서드로 추출한다.
- 시점 표현 대신 사실 형태로 작성한다.

#### 금지 사항

- 결정 내러티브 금지
- 검사기가 보장하는 내용 반복 금지
- 주석 처리된 코드 금지
- 작성자·날짜 기록 금지

#### TODO 규칙

- TODO는 `// TODO(#이슈번호): 내용` 형식만 허용한다.
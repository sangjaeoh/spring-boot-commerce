# Code Quality

## 언제

- 포맷·null·정적분석 게이트의 강제 범위를 확인할 때.
- 도구 버전을 결정하거나 변경할 때.
- 새 모듈에 품질 게이트를 배선할 때.

## 규칙

- 이 문서는 포맷·null·정적분석 도구만 다룬다.
- 계층 의존·엔티티 비노출 등 경계 강제는 컨벤션 플러그인·아키텍처 테스트가 담당한다.
- 강제 대상 불변식은 [architecture](architecture.md)의 빌드 강제 규칙이 소유한다.
- Spotless·NullAway·Error Prone은 `convention.java-base`가 전 JVM 모듈에 일괄 적용한다.
- `convention.java-common`이 Lombok·H2 의존성을 차단한다.
- Lombok은 사용하지 않는다.
- H2는 사용하지 않는다.
- 영속 테스트는 실 PostgreSQL(Testcontainers) 기준으로 수행한다.

### Spotless (Palantir Java Format)

- `spotlessCheck`를 빌드 게이트로 사용한다.
- 포맷 수정은 `./gradlew spotlessApply`로 수행한다.
- 전체 품질 게이트는 `./gradlew build`로 수행한다.
- 수동 스타일 문서는 유지하지 않는다.
- 포맷 규칙은 자동 교정 기준만 사용한다.

### NullAway + JSpecify

- NullAway는 베이스 패키지의 프로덕션 소스 전체에 적용한다.
- 미주석 참조는 non-null로 간주한다.
- nullable 값은 `@Nullable`로 명시한다.
- 검사 범위는 베이스 패키지 접두 배선으로 결정한다.
- `@NullMarked` 누락 여부와 관계없이 배선된 패키지는 검사한다.
- 각 모듈의 `@NullMarked`는 JSpecify 의미 선언으로 유지한다.
- 베이스 패키지 밖 모듈은 검사하지 않는다.
- JPA가 채우는 엔티티 필드는 초기화 검사에서 제외한다.
- 클래스 단위 `@SuppressWarnings("NullAway.Init")`는 사용하지 않는다.
- 모든 영속 non-null 필드에 매핑 애노테이션을 명시한다.
- nullable 필드만 `@Nullable`로 유지한다.

### 정적분석 억제

- NullAway·Error Prone 위반은 설계 변경으로 해결한다.
- `@SuppressWarnings`는 예외적으로만 사용한다.
- 억제 범위는 필드·메서드 단위 최소 범위로 제한한다.
- 비자명한 억제에는 이유 주석을 남긴다.

### Error Prone

- Error Prone은 버그 패턴과 Javadoc 구조를 컴파일 시점에 검사한다.
- Javadoc 산문 규약은 [coding-conventions](coding-conventions.md)를 따른다.

### 버전

- 버전 정본은 `gradle/libs.versions.toml`이다.
- 아래 표는 참고용이며 불일치 시 toml을 따른다.
- 의존성은 `libs.*` 카탈로그 별칭으로만 선언한다.
- 빌드스크립트에 버전 리터럴을 두지 않는다.

| 항목 | 권장 baseline |
|---|---|
| Java | 25 (LTS) |
| Spotless (Palantir Java Format) | 8.8.x (Palantir 2.94.x) |
| JSpecify | 1.0.x |
| NullAway | 0.13.x |
| Error Prone | 2.50.x |
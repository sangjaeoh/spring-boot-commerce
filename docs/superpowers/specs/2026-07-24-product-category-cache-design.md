# 상품 카테고리 조회 캐시 도입 설계

## 배경

읽기 많고 변경 적은 조회부터 캐시를 도입한다. 이번 라운드 대상은 `CategoryReader.getCategories()` 하나다. 캐시 정책은 `docs/caching.md`가 이미 소유하며, 이 설계는 그 정책을 이번 대상에 적용하는 구체화다. 캐시 저장소·구성 인프라(`@EnableCaching`·`CacheManager`·직렬화·오류 처리)가 레포에 전무해 이번이 최초 도입이다 — 대상 캐시 자체와 재사용 가능한 인프라를 함께 만든다.

`ProductReader`(단건·컬렉션·페이지)·`ProductVariantReader`·`ProductImageReader`는 이번 범위 밖이다. 특히 `ProductReader`의 페이지 조회 3종은 `Page` 대신 `{대상}PageInfo` 신설이 필요해 계약 변경 폭이 커서 별도 합의 대상으로 남긴다.

## 캐시 대상·이름·키·TTL·저장소

| 항목 | 값 |
|---|---|
| 대상 조회 | `CategoryReader.getCategories()` |
| 캐시 이름 | `product:category:v1` |
| 키 | `'all'` (SpEL 리터럴 고정 — 메서드가 파라미터 없어 기본 키 생성기에 맡기지 않고 명시) |
| TTL | 10분 |
| 저장소 | Redis, 캐시 전용 커넥션(아래 인프라 절 참고) |
| 로컬 채택 예외 | 없음 |
| evict | `allEntries = true` — 단일 리스트 엔트리 캐시라 쓰기 파라미터로 키 재구성할 대상이 없음 |

`CategoryInfo`는 이미 경계 모델(record)이고 `List<CategoryInfo>`는 캐시 값 허용 타입(Info 컬렉션)에 그대로 해당해 계약 변경이 없다.

## 도메인 모듈 변경 (domain-product)

- `CategoryCacheNames`(`application/provided` 패키지) 신설: `public static final String CATEGORY = "product:category:v1"`.
- `DefaultCategoryReader.getCategories()`: `@Transactional` 제거, `@Cacheable(cacheNames = CategoryCacheNames.CATEGORY, key = "'all'")` 부착. 실제 조회는 `TransactionalCategoryReader.getCategories()`로 위임.
- `TransactionalCategoryReader`(package-private, `application` 패키지 신설): `@Transactional(readOnly = true)`로 리포지토리 호출·`CategoryInfo` 변환 수행. `CategoryReader`(provided 계약)를 구현하지 않는다 — 컨텍스트 안에 `CategoryReader` 구현체가 `DefaultCategoryReader` 하나만 존재하도록 유지(DI 모호성 차단).
- `DefaultCategoryAppender.create()`·`DefaultCategoryModifier.rename()`·`DefaultCategoryRemover.delete()` 각각에 `@CacheEvict(cacheNames = CategoryCacheNames.CATEGORY, allEntries = true)` 부착. 셋 다 이미 `@Transactional`이라 커밋 후 지연 evict가 자연스럽게 걸린다.

캐시 구조 자체는 `docs/caching.md`의 기존 합의(허용 계층 2곳 한정, 위임 서비스는 provided 계약 미보유)를 그대로 따른다 — 도메인 모듈 밖 프록시·데코레이터 레이어 도입은 검토했으나(별도 절 참고) 채택하지 않는다.

## 검토했으나 채택하지 않은 대안: 캐시 프록시/데코레이터 레이어

별도 클래스(`CachingCategoryReader`)가 `CategoryReader`를 구현하며 `@Cacheable`을 지니고, 캐시 없는 `DefaultCategoryReader`를 감싸는 방식을 검토했다. 앱마다 어느 구현체를 `CategoryReader` 빈으로 조립할지 명시적으로 고를 수 있어 앱별 캐시 온·오프가 설정 코드에서 더 눈에 띈다는 장점이 있다.

채택하지 않은 이유:
- 컨텍스트 안에 `CategoryReader` 구현체가 둘(`Default`·`Caching`)이 되어 `@Primary` 등으로 우선순위를 정해야 한다 — 다른 코드가 실수로 캐시 없는 구현체를 직접 참조하면 캐시가 조용히 우회되는 DI 모호성이 생긴다. `docs/caching.md`의 "위임 서비스는 provided 계약을 만들지 않는다" 규칙이 정확히 이 모호성을 막기 위한 것으로 판단된다.
- 메서드가 많은 Reader(예: `ProductReader`)에 적용하면 프록시 클래스가 인터페이스 전 메서드를 위임 보일러플레이트로 재작성해야 해 어노테이션 방식보다 코드량이 늘고 스케일이 나쁘다.
- 앱별 캐시 온·오프라는 목적 자체는 현재 방식(앱의 `@EnableCaching`·캐시 이름 등록 유무)으로도 이미 동일하게 달성된다 — 프록시 레이어가 새로 주는 능력은 없고 가독성 트레이드오프만 남는다.
- 이 대안 채택은 `docs/caching.md`의 허용 계층·위임 서비스 규칙 자체를 바꾸는 저장소 전체 컨벤션 변경이라 이번 카테고리 캐시 하나로 결정할 범위를 넘는다.

## 인프라: common-cache 모듈 신설

`convention.common-module` 적용(`common-core` 방향 단방향 의존만). 캐시 저장소 스타터·클라이언트는 앱만 실어야 하므로 `common-cache`는 spring-boot-starter-data-redis 의존을 갖지 않는다 — 앱이 넘긴 `RedisConnectionFactory`·이름별 등록 정보를 조립하는 코드만 소유한다.

`common-cache`가 갖는 것(범용, 도메인 무지):
- 캐시 매니저 조립기: `{cacheName, ttl, valueType}` 등록 목록을 받아 `RedisCacheManager`를 만든다. `withInitialCacheConfigurations(...)` + `disableCreateOnMissingCache()`로 미등록 이름의 즉석 생성을 막는다.
- 이름별 값 타입 고정 Jackson 직렬화기(타입 정보 임베딩 방식 미사용).
- `LoggingCacheErrorHandler implements CacheErrorHandler`: get 오류는 로그 후 미스로 강등, put 오류는 로그 후 무시.
- 트랜잭션 어웨어 evict 데코레이터: `Cache.evict()`/`clear()`를 커밋 후 실행되도록 감싸고, 실패 시 로그 + Micrometer 카운터 `cache.evict.errors`(태그: 캐시 이름) 증가. Spring 기본 `TransactionAwareCacheManagerProxy`는 지연은 되나 오류 처리·메트릭 태깅을 끼워 넣을 수 없어 자체 데코레이터로 감싼다.
- `enableStatistics()`로 캐시 통계 활성화.

## Redis 커넥션

기존 `infra-redis`의 공유 `RedisConnectionFactory`(idempotency·rate-limit·refresh-token·shedlock이 사용, 명시 timeout 없음)는 그대로 둔다. 캐시는 같은 Redis 서버(새 인프라 자원 아님)를 가리키되 별도의 전용 `LettuceConnectionFactory`를 새로 구성한다 — 클라이언트 인스턴스와 timeout만 분리한다.

- command timeout: 300ms (일반 Redis 응답은 수 ms대라 여유 충분, 장애 시 빠르게 미스로 강등).
- 이 값은 DB 조회 지연보다 짧다는 전제로 정했다.
- 캐시 전용 커넥션에는 헬스 인디케이터를 새로 달지 않는다 — 미스 강등 설계와 모순되는 readiness DOWN 전파를 막기 위함(공유 커넥션의 기존 redis 헬스 인디케이터는 다른 실제 상태 저장 용도로 계속 필요해 그대로 둔다).

## 앱 구성 (app-api, app-admin)

두 앱 모두 동일하게 구성한다(app-admin도 캐싱 켬 — evict가 쓰기 서비스에 있어 관리자 자신의 읽기-쓰기 신선도는 이미 보장되므로 별도로 끌 이유가 약함). `app-batch`는 `domain-product`를 `testImplementation`으로만 의존해 런타임 임베드가 아니므로 대상에서 제외한다.

각 앱에 추가:
- `@EnableCaching`.
- 캐시 전용 `LettuceConnectionFactory` 빈(위 300ms 설정).
- `common-cache` 조립기에 `product:category:v1 → (10분, List<CategoryInfo>)` 등록.

## 아키텍처 테스트 추가

`module-tests/test-architecture`의 `ArchitectureTest.java`에 `docs/caching.md`의 "강제와 리뷰"가 요구하는 항목을 추가한다. 이번 카테고리 캐시 하나에 국한되지 않고 앞으로 어떤 도메인이 캐시를 붙이든 적용되는 범용 규칙이다.

- `@Cacheable` 위치 제한(도메인 `application`의 Reader provided 구현 / query 모듈 provided 구현만).
- `@CacheEvict` 위치 제한(같은 모듈 쓰기 서비스만).
- 쓰기 서비스·`Validator`의 같은 모듈 `@Cacheable` 메서드 직접 호출 금지.
- `@Cacheable`과 `@Transactional`의 메서드 단위 동시 선언 금지(클래스 레벨 포함).
- `@Cacheable` 메서드의 `@Nullable` 반환 선언 금지.
- `@CachePut`·`condition`·`unless`·`beforeInvocation` 사용 금지.
- `@Cacheable` 캐시 이름마다 같은 모듈 동명 `@CacheEvict` 존재(TTL-only query 모듈 제외).
- `@CacheEvict` 캐시 이름마다 같은 모듈 동명 `@Cacheable` 존재(고아 evict 금지).
- 캐시 이름 형식(버전 세그먼트 포함) 검사.
- 같은 모듈에서 버전만 다른 같은 이름의 공존 금지.
- `@Cacheable`과 키 지정 `@CacheEvict`의 `key` 명시 검사(`allEntries = true` evict는 제외).
- 애노테이션 캐시 이름 값 전수와 이름 상수 집합의 일치.
- `@Cacheable` 반환 타입이 캐시 값 허용 타입인지 검사.
- (별도, 의존 그래프 기반) `@Cacheable` 가진 모듈을 컴포넌트 스캔 대상으로 embed하는 앱은 캐시 구성(`@EnableCaching` 포함)을 동반해야 한다 — 런타임 전용 의존 앱(`app-migration`) 제외.

## 테스트

`docs/caching.md`의 "evict 짝이 있는 합의된 캐시 이름마다 대표 쓰기 경로 하나로 무효화 시나리오를 파생한다" 규칙에 따라 대표 경로 하나만 둔다: `rename`.

시나리오: `getCategories()` 조회로 캐시를 채운다 → `CategoryModifier.rename()` 호출로 실제 커밋되게 한다 → 재조회 시 변경된 이름이 즉시 반영됨을 검증한다. `@Transactional` 슬라이스의 롤백 격리에서는 커밋 후 evict가 돌지 않으므로, 실제 커밋이 일어나는 풀 통합 테스트로 작성한다. 캐시 저장소는 Testcontainers Redis를 쓴다(app-api 모듈에 이미 테스트 의존 있음).

## 문서 반영

`docs/project/domain_model.md` 끝에 독립 섹션 `## 캐시`를 신설하고 다음 표를 둔다.

| 이름 | 대상 조회 | TTL | 로컬 채택 예외 | 사유 |
|---|---|---|---|---|
| `product:category:v1` | `CategoryReader.getCategories()` | 10분 | 없음(Redis) | 저변경·소량 데이터, 관리자 변경 빈도 낮고 스토어프론트 조회 빈도 높음 |

## 완료 판정 (docs/caching.md 기준)

- `product:category:v1`이 위 표에 반영됨.
- `@Cacheable` 선언·evict 짝·이름 상수·TTL 구성·시리얼라이저 등록·표 행이 `product:category:v1` 하나로 일치.
- 값 형상·키 의미 변경 없어 버전(`v1`) 유지.
- `rename` 무효화 시나리오가 시나리오 목록에 있음(`docs/testing.md` 시나리오 소유 절차를 따름).
- 구성·장애 배선(에러 핸들러·timeout 300ms·통계·헬스 그룹 제외)이 위 절과 일치.
- 아키텍처 테스트를 포함한 빌드 통과.

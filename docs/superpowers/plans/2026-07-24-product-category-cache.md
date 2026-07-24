# 상품 카테고리 조회 캐시 도입 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `CategoryReader.getCategories()`에 Redis 기반 투명 캐시(`product:category:v1`, TTL 10분)를 도입하고, 재사용 가능한 캐시 인프라(`common-cache`)와 아키텍처 강제 장치를 함께 만든다.

**Architecture:** `common-cache`(신설 common 모듈)가 저장소 무관 evict-안전 트랜잭션 어웨어 `CacheManager` 데코레이터와 Redis 전용 `RedisCacheManager` 조립기를 소유한다. `domain-product`는 `DefaultCategoryReader`에 `@Cacheable`을, `CategoryAppender`·`CategoryModifier`·`CategoryRemover`에 `@CacheEvict(allEntries=true)`를 붙이고 캐시 미스 경로를 `TransactionalCategoryReader` 위임 서비스로 분리한다. `app-api`·`app-admin`은 각자 캐시 전용 Redis 커넥션(300ms timeout)과 `CacheManager`/`CacheErrorHandler` 빈을 동일하게 구성한다.

**Tech Stack:** Spring Boot 4.1.0 / Spring Framework 7.0.8, Spring Data Redis 4.1.0(Jackson 3 기반 `JacksonJsonRedisSerializer`), Micrometer, ArchUnit 1.4.1, Testcontainers(PostgreSQL·Redis), JUnit 5, AssertJ.

## Global Constraints

- 캐시 이름: `product:category:v1` (형식 `{도메인}:{대상}:v{n}`, 상수는 `CategoryCacheNames`가 소유, `application/provided` 패키지).
- 키: SpEL 리터럴 `'all'` (파라미터 없는 메서드라 기본 키 생성기에 맡기지 않는다).
- TTL: 10분. evict: `allEntries = true`.
- 저장소: Redis, 캐시 전용 `LettuceConnectionFactory`(command timeout 300ms), 기존 공유 Redis 커넥션과 분리.
- `@Cacheable`은 `DefaultCategoryReader`(도메인 `application`의 provided 구현)에만 둔다. `@Cacheable`과 `@Transactional`을 같은 메서드/클래스에 동시 선언하지 않는다.
- 미스 경로 위임 서비스명은 `TransactionalCategoryReader`로 고정하고 provided 계약을 만들지 않는다.
- 캐시 값은 `List<CategoryInfo>`(경계 모델 컬렉션)만 허용, `@Nullable` 반환 금지.
- `@CachePut`·`condition`·`unless`·`beforeInvocation`은 어디에도 쓰지 않는다.
- 값 직렬화는 캐시 이름별 타입 고정 Jackson 직렬화기로 한다(default typing 금지).
- evict는 transaction-aware 구성으로 커밋 후 실행하고, 실패는 로그 + `cache.evict.errors`(태그: 캐시 이름) 카운터로 흡수한다 — 조회·쓰기 실패로 전파하지 않는다.
- get/put 오류는 `CacheErrorHandler`가 로그 후 흡수한다(Spring 내장 `org.springframework.cache.interceptor.LoggingCacheErrorHandler` 재사용).
- app-api·app-admin 둘 다 동일하게 캐시를 켠다. app-batch·app-migration은 대상에서 제외한다.
- 캐시 저장소 스타터·클라이언트(Lettuce/Jedis, `spring-boot-starter-data-redis`)는 앱(app-api·app-admin)만 싣는다. `common-cache`는 `spring-data-redis`(추상화 라이브러리, POM상 lettuce·jedis가 `optional`이라 클라이언트가 전이되지 않는다)만 싣는다.
- 새 common 모듈(`common-cache`)은 `convention.common-module`을 적용해 `common-core` 방향으로만 프로젝트 의존 가능하다(이번 변경은 `common-core` 프로젝트 의존이 필요 없다).
- 모든 새 패키지는 `@NullMarked` package-info를 둔다(JSpecify).

---

## 검증된 핵심 API (구현 중 재확인 불필요)

로컬 Gradle 캐시의 실제 jar를 `javap`로 디컴파일해 아래를 확인했다.

- `org.springframework.data.redis.serializer.JacksonJsonRedisSerializer<T>`: 생성자 `(tools.jackson.databind.ObjectMapper, tools.jackson.databind.JavaType)` — Jackson 3 타입 고정 직렬화기.
- `org.springframework.data.redis.cache.RedisCacheManager.builder(RedisConnectionFactory)` → `.cacheDefaults(...)`, `.withInitialCacheConfigurations(Map)`, `.disableCreateOnMissingCache()`, `.enableStatistics()`, `.build()`.
- `org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()` → `.entryTtl(Duration)`, `.disableCachingNullValues()`, `.serializeValuesWith(RedisSerializationContext.SerializationPair)`.
- `org.springframework.cache.interceptor.LoggingCacheErrorHandler`(spring-context에 내장, 커스텀 구현 불필요): `handleCacheGetError`·`handleCachePutError`·`handleCacheEvictError`·`handleCacheClearError` 전부 로그만 하고 예외를 던지지 않는다(바이트코드에 `athrow` 없음 확인).
- `org.springframework.cache.transaction.TransactionAwareCacheManagerProxy`/`TransactionAwareCacheDecorator`는 Spring Framework 7에서 `spring-context`가 아니라 `spring-context-support`로 이전됐다. 더 중요하게, `TransactionAwareCacheDecorator`의 `afterCommit()` 지연 실행 경로(`TransactionSynchronizationUtils.invokeAfterCommit`)는 위임 evict가 던진 예외를 흡수하지 않고 그대로 커밋 경로로 전파한다(바이트코드 확인: `invokeAfterCommit`에 try/catch 없음). 그래서 Spring 내장 `TransactionAwareCacheManagerProxy`를 그대로 쓰지 않고, evict 실패를 흡수하는 자체 데코레이터(`EvictSafeCacheManager`/`EvictSafeCache`)를 새로 만든다.
- `org.springframework.cache.annotation.CachingConfigurer`: `cacheManager()`·`cacheResolver()`·`keyGenerator()`·`errorHandler()` 전부 파라미터 없는 default 메서드 — `@Configuration` 클래스가 구현하고 `@Bean`으로 오버라이드하는 방식이 표준.

---

### Task 1: common-cache 모듈 스켈레톤 + 버전 카탈로그

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `module-common/common-cache/build.gradle.kts`
- Create: `module-common/common-cache/src/main/java/com/commerce/common/cache/package-info.java`
- Create: `module-common/common-cache/src/test/java/com/commerce/common/cache/CacheRegistrationTest.java` (컴파일 확인용 최소 테스트, Task 2에서 실제 내용 채움)

**Interfaces:**
- Produces: Gradle 모듈 `:module-common:common-cache`, 베이스 패키지 `com.commerce.common.cache`.

- [ ] **Step 1: 버전 카탈로그에 신규 라이브러리 좌표 추가**

`gradle/libs.versions.toml`의 `[libraries]` 섹션 끝(또는 관련 섹션 근처)에 추가한다. 버전은 모두 Spring Boot BOM(`spring-tx`·`spring-data-redis`) 또는 별도 관리(micrometer-core는 Boot BOM이 관리) — `version.ref` 없이 좌표만 선언한다(기존 `spring-context`·`spring-jdbc`와 같은 패턴).

```toml
# common-cache — 캐시 인프라(트랜잭션 지원·evict 데코레이터). 버전은 Spring Boot BOM
spring-tx = { module = "org.springframework:spring-tx" }
# common-cache — RedisCacheManager·타입 고정 직렬화기 조립(추상화만, lettuce/jedis는 POM상 optional이라 전이되지 않는다). 버전은 Spring Boot BOM
spring-data-redis = { module = "org.springframework.data:spring-data-redis" }
# common-cache — evict 실패 카운터(cache.evict.errors). 버전은 Spring Boot BOM
micrometer-core = { module = "io.micrometer:micrometer-core" }
```

- [ ] **Step 2: settings.gradle.kts에 모듈 등록**

`include("module-common:common-web")` 다음 줄에 추가한다.

```kotlin
include("module-common:common-cache")
```

- [ ] **Step 3: build.gradle.kts 작성**

```kotlin
plugins {
    id("convention.common-module")
}

dependencies {
    implementation(
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"),
    )
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.data.redis)
    implementation(libs.jackson.databind)
    implementation(libs.micrometer.core)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 4: package-info 작성**

```java
@NullMarked
package com.commerce.common.cache;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 5: 컴파일 확인용 최소 테스트 작성**

아직 프로덕션 코드가 없으므로 모듈 배선만 확인하는 자리표시 테스트를 둔다(Task 2에서 실제 `CacheRegistration` 테스트로 대체).

```java
package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheRegistrationTest {

    @Test
    void moduleWiringCompiles() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew :module-common:common-cache:test`
Expected: BUILD SUCCESSFUL, 테스트 1개 통과.

- [ ] **Step 7: 커밋**

```bash
git add gradle/libs.versions.toml settings.gradle.kts module-common/common-cache
git commit -m "build: common-cache 모듈 스켈레톤 추가"
```

---

### Task 2: CacheRegistration + RedisCacheManagerFactory

**Files:**
- Create: `module-common/common-cache/src/main/java/com/commerce/common/cache/CacheRegistration.java`
- Create: `module-common/common-cache/src/main/java/com/commerce/common/cache/RedisCacheManagerFactory.java`
- Modify: `module-common/common-cache/src/test/java/com/commerce/common/cache/CacheRegistrationTest.java` (Task 1의 자리표시 테스트를 대체)
- Create: `module-common/common-cache/src/test/java/com/commerce/common/cache/RedisCacheManagerFactoryTest.java`

**Interfaces:**
- Produces: `record CacheRegistration(String name, java.time.Duration ttl, tools.jackson.databind.JavaType valueType)`.
- Produces: `RedisCacheManagerFactory.build(RedisConnectionFactory connectionFactory, List<CacheRegistration> registrations) -> RedisCacheManager`.
- Consumes: (Task 1 모듈 배선).

- [ ] **Step 1: CacheRegistration 실패하는 테스트 작성**

```java
package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.type.TypeFactory;

class CacheRegistrationTest {

    @Test
    void holdsNameTtlAndValueType() {
        var valueType = TypeFactory.createDefaultInstance().constructType(String.class);

        var registration = new CacheRegistration("product:category:v1", Duration.ofMinutes(10), valueType);

        assertThat(registration.name()).isEqualTo("product:category:v1");
        assertThat(registration.ttl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(registration.valueType()).isEqualTo(valueType);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :module-common:common-cache:test`
Expected: FAIL — `CacheRegistration` 클래스가 없어 컴파일 오류.

- [ ] **Step 3: CacheRegistration 구현**

```java
package com.commerce.common.cache;

import java.time.Duration;
import tools.jackson.databind.JavaType;

/** 캐시 이름 하나의 TTL·값 타입 등록 정보다. 이름별 TTL·타입 고정 직렬화기 조립의 입력이다. */
public record CacheRegistration(String name, Duration ttl, JavaType valueType) {}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :module-common:common-cache:test --tests CacheRegistrationTest`
Expected: PASS.

- [ ] **Step 5: RedisCacheManagerFactory 실패하는 테스트 작성**

`RedisConnectionFactory`는 실제 연결 없이 구성만 검증하므로 로컬호스트를 가리키는 `LettuceConnectionFactory`를 생성만 하고(연결하지 않음) 사용한다.

```java
package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.type.TypeFactory;

class RedisCacheManagerFactoryTest {

    @Test
    void registersOnlyDeclaredCacheNamesWithConfiguredTtl() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 0));
        var valueType = TypeFactory.createDefaultInstance().constructType(String.class);
        var registrations = List.of(new CacheRegistration("product:category:v1", Duration.ofMinutes(10), valueType));

        var cacheManager = RedisCacheManagerFactory.build(connectionFactory, registrations);
        // RedisCacheManager는 InitializingBean이라 실제 앱에서는 @Bean 등록 시 컨테이너가 이 호출을 대신한다.
        // 컨테이너 없이 직접 생성하는 이 테스트에서는 초기화를 수동으로 트리거해야 getCacheConfigurations()가 채워진다.
        cacheManager.afterPropertiesSet();

        assertThat(cacheManager.getCacheConfigurations()).containsOnlyKeys("product:category:v1");
        assertThat(cacheManager.getCacheConfigurations().get("product:category:v1").getTtlFunction())
                .isNotNull();
        assertThat(cacheManager.isAllowRuntimeCacheCreation()).isFalse();
    }
}
```

- [ ] **Step 6: 컴파일 실패 확인**

Run: `./gradlew :module-common:common-cache:test --tests RedisCacheManagerFactoryTest`
Expected: FAIL — `RedisCacheManagerFactory`가 없어 컴파일 오류.

- [ ] **Step 7: RedisCacheManagerFactory 구현**

```java
package com.commerce.common.cache;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.json.JsonMapper;

/** 이름별 TTL·값 타입 등록 목록으로 RedisCacheManager를 조립한다. 등록되지 않은 이름은 생성하지 않는다. */
public final class RedisCacheManagerFactory {

    private RedisCacheManagerFactory() {}

    public static RedisCacheManager build(
            RedisConnectionFactory connectionFactory, List<CacheRegistration> registrations) {
        JsonMapper mapper = JsonMapper.builder().build();

        Map<String, RedisCacheConfiguration> configurations = registrations.stream()
                .collect(Collectors.toMap(CacheRegistration::name, registration -> RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(registration.ttl())
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(mapper, registration.valueType())))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
                .withInitialCacheConfigurations(configurations)
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :module-common:common-cache:test`
Expected: PASS (2개 테스트 클래스 전부).

- [ ] **Step 9: 커밋**

```bash
git add module-common/common-cache
git commit -m "feat: CacheRegistration·RedisCacheManagerFactory 추가"
```

---

### Task 3: EvictSafeCacheManager (트랜잭션 어웨어 evict 흡수)

**Files:**
- Create: `module-common/common-cache/src/main/java/com/commerce/common/cache/EvictSafeCacheManager.java`
- Create: `module-common/common-cache/src/main/java/com/commerce/common/cache/EvictSafeCache.java`
- Create: `module-common/common-cache/src/test/java/com/commerce/common/cache/EvictSafeCacheManagerTest.java`

**Interfaces:**
- Consumes: (없음 — spring-context의 `Cache`/`CacheManager`, spring-tx의 `TransactionSynchronizationManager`, micrometer-core의 `MeterRegistry`/`Counter`만 사용).
- Produces: `new EvictSafeCacheManager(CacheManager delegate, MeterRegistry meterRegistry) implements CacheManager` — Task 6(app 배선)이 소비한다.

- [ ] **Step 1: 실패하는 테스트 작성 — 트랜잭션 없음, 즉시 evict**

`org.springframework.cache.concurrent.ConcurrentMapCacheManager`를 델리게이트로 써서 Redis 없이 검증한다.

```java
package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EvictSafeCacheManagerTest {

    private final ConcurrentMapCacheManager delegate = new ConcurrentMapCacheManager("categories");
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final EvictSafeCacheManager cacheManager = new EvictSafeCacheManager(delegate, meterRegistry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsImmediatelyWithoutActiveTransaction() {
        var cache = cacheManager.getCache("categories");
        cache.put("all", "stale");

        cache.evict("all");

        assertThat(delegate.getCache("categories").get("all")).isNull();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :module-common:common-cache:test --tests EvictSafeCacheManagerTest`
Expected: FAIL — `EvictSafeCacheManager`가 없어 컴파일 오류.

- [ ] **Step 3: EvictSafeCache 구현**

```java
package com.commerce.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * evict·clear를 커밋 후로 지연시키고 실패를 로그·메트릭으로 흡수하는 {@link Cache} 데코레이터다.
 *
 * <p>Spring 내장 {@code TransactionAwareCacheDecorator}는 지연 실행 중 예외를 흡수하지 않고 커밋 경로로
 * 전파하므로 쓰지 않는다.
 */
final class EvictSafeCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(EvictSafeCache.class);

    private final Cache delegate;
    private final Counter evictErrorCounter;

    EvictSafeCache(Cache delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.evictErrorCounter = Counter.builder("cache.evict.errors")
                .tag("cache.name", delegate.getName())
                .register(meterRegistry);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public @Nullable ValueWrapper get(Object key) {
        return delegate.get(key);
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
        return delegate.get(key, valueLoader);
    }

    @Override
    public CompletableFuture<?> retrieve(Object key) {
        return delegate.retrieve(key);
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        return delegate.retrieve(key, valueLoader);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        delegate.put(key, value);
    }

    @Override
    public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        runAfterCommitOrNow(() -> delegate.evict(key));
    }

    @Override
    public boolean evictIfPresent(Object key) {
        return delegate.evictIfPresent(key);
    }

    @Override
    public void clear() {
        runAfterCommitOrNow(delegate::clear);
    }

    @Override
    public boolean invalidate() {
        return delegate.invalidate();
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("evict 실패 — cache={}", delegate.getName(), ex);
                    evictErrorCounter.increment();
                }
            }
        });
    }
}
```

- [ ] **Step 4: EvictSafeCacheManager 구현**

```java
package com.commerce.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** evict 실패를 흡수하는 트랜잭션 어웨어 {@link CacheManager}다. 저장소 무관 — 임의 델리게이트를 감쌀 수 있다. */
public final class EvictSafeCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final MeterRegistry meterRegistry;

    public EvictSafeCacheManager(CacheManager delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public @Nullable Cache getCache(String name) {
        Cache target = delegate.getCache(name);
        return target == null ? null : new EvictSafeCache(target, meterRegistry);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :module-common:common-cache:test --tests EvictSafeCacheManagerTest`
Expected: PASS.

- [ ] **Step 6: 트랜잭션 커밋 후 지연 evict·evict 실패 흡수 시나리오 추가**

`EvictSafeCacheManagerTest`에 아래 두 테스트를 추가한다. 두 번째 테스트는 evict가 던지는 실패를 흡수하는지 확인하기 위해 `Cache`·`CacheManager`를 직접 구현한 익명 클래스로 델리게이트를 만든다(Mockito 미사용 — 이 모듈은 프레임워크 최소 의존이라 순수 익명 구현으로 충분하다).

```java
    @Test
    void deferEvictUntilAfterCommitWhenTransactionActive() {
        var cache = cacheManager.getCache("categories");
        cache.put("all", "stale");
        TransactionSynchronizationManager.initSynchronization();

        cache.evict("all");
        assertThat(delegate.getCache("categories").get("all")).isNotNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(delegate.getCache("categories").get("all")).isNull();
    }

    @Test
    void swallowsEvictFailureAndIncrementsErrorCounter() {
        Cache throwingCache = new Cache() {
            @Override
            public String getName() {
                return "categories";
            }

            @Override
            public Object getNativeCache() {
                return this;
            }

            @Override
            public ValueWrapper get(Object key) {
                return null;
            }

            @Override
            public <T> T get(Object key, Class<T> type) {
                return null;
            }

            @Override
            public <T> T get(Object key, Callable<T> valueLoader) {
                return null;
            }

            @Override
            public CompletableFuture<?> retrieve(Object key) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
                return valueLoader.get();
            }

            @Override
            public void put(Object key, Object value) {}

            @Override
            public ValueWrapper putIfAbsent(Object key, Object value) {
                return null;
            }

            @Override
            public void evict(Object key) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void clear() {}
        };
        CacheManager throwingDelegate = new CacheManager() {
            @Override
            public Cache getCache(String name) {
                return throwingCache;
            }

            @Override
            public java.util.Collection<String> getCacheNames() {
                return java.util.List.of("categories");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EvictSafeCacheManager manager = new EvictSafeCacheManager(throwingDelegate, registry);
        TransactionSynchronizationManager.initSynchronization();

        manager.getCache("categories").evict("all");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(registry.get("cache.evict.errors").tag("cache.name", "categories").counter().count())
                .isEqualTo(1.0);
    }
```

위 두 테스트를 반영해 `EvictSafeCacheManagerTest.java` 전체를 필요한 import 포함해 아래처럼 정리한다(Step 1의 첫 테스트도 포함한 최종본).

```java
package com.commerce.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EvictSafeCacheManagerTest {

    private final ConcurrentMapCacheManager delegate = new ConcurrentMapCacheManager("categories");
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final EvictSafeCacheManager cacheManager = new EvictSafeCacheManager(delegate, meterRegistry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evictsImmediatelyWithoutActiveTransaction() {
        var cache = cacheManager.getCache("categories");
        cache.put("all", "stale");

        cache.evict("all");

        assertThat(delegate.getCache("categories").get("all")).isNull();
    }

    @Test
    void deferEvictUntilAfterCommitWhenTransactionActive() {
        var cache = cacheManager.getCache("categories");
        cache.put("all", "stale");
        TransactionSynchronizationManager.initSynchronization();

        cache.evict("all");
        assertThat(delegate.getCache("categories").get("all")).isNotNull();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(delegate.getCache("categories").get("all")).isNull();
    }

    @Test
    void swallowsEvictFailureAndIncrementsErrorCounter() {
        Cache throwingCache = new Cache() {
            @Override
            public String getName() {
                return "categories";
            }

            @Override
            public Object getNativeCache() {
                return this;
            }

            @Override
            public ValueWrapper get(Object key) {
                return null;
            }

            @Override
            public <T> T get(Object key, Class<T> type) {
                return null;
            }

            @Override
            public <T> T get(Object key, Callable<T> valueLoader) {
                return null;
            }

            @Override
            public CompletableFuture<?> retrieve(Object key) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
                return valueLoader.get();
            }

            @Override
            public void put(Object key, Object value) {}

            @Override
            public ValueWrapper putIfAbsent(Object key, Object value) {
                return null;
            }

            @Override
            public void evict(Object key) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public void clear() {}
        };
        CacheManager throwingDelegate = new CacheManager() {
            @Override
            public Cache getCache(String name) {
                return throwingCache;
            }

            @Override
            public List<String> getCacheNames() {
                return List.of("categories");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EvictSafeCacheManager manager = new EvictSafeCacheManager(throwingDelegate, registry);
        TransactionSynchronizationManager.initSynchronization();

        manager.getCache("categories").evict("all");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(registry.get("cache.evict.errors").tag("cache.name", "categories").counter().count())
                .isEqualTo(1.0);
    }
}
```

- [ ] **Step 7: 전체 테스트 통과 확인**

Run: `./gradlew :module-common:common-cache:test`
Expected: PASS (3개 테스트 메서드 전부).

- [ ] **Step 8: 커밋**

```bash
git add module-common/common-cache
git commit -m "feat: EvictSafeCacheManager로 evict 실패 흡수·지연 실행 추가"
```

---

### Task 4: domain-product — CategoryCacheNames + DefaultCategoryReader 캐시 분리

**Files:**
- Create: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/provided/CategoryCacheNames.java`
- Create: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/TransactionalCategoryReader.java`
- Modify: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/DefaultCategoryReader.java`
- Create: `module-domains/domain-product/src/test/java/com/commerce/domain/product/application/CategoryPersistenceTest.java`

**Interfaces:**
- Produces: `CategoryCacheNames.CATEGORY = "product:category:v1"` (public 상수, 모듈 밖 임포트 허용).
- Consumes: (없음 — 이 태스크는 common-cache에 의존하지 않는다. `@Cacheable`은 spring-context 애노테이션이며 domain-product는 common-jpa 경유로 이미 spring-context를 갖고 있다.)

- [ ] **Step 1: CategoryCacheNames 작성**

```java
package com.commerce.domain.product.application.provided;

/** 상품 도메인 캐시 이름 상수다. */
public final class CategoryCacheNames {

    public static final String CATEGORY = "product:category:v1";

    private CategoryCacheNames() {}
}
```

- [ ] **Step 2: 실패하는 영속 슬라이스 테스트 작성**

기존 `ProductPersistenceTest.java`와 같은 패턴(`@DataJpaTest` + `@Import`로 package-private 구현체 주입 + Testcontainers PostgreSQL)을 따른다.

```java
package com.commerce.domain.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryReader;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * category의 영속 이음새를 실 PostgreSQL로 검증한다.
 *
 * <p>이 슬라이스는 {@code @EnableCaching}이 없어 {@code @Cacheable}이 순수 메타데이터로만 존재한다 —
 * {@link DefaultCategoryReader}의 캐시 분리(미스 경로 위임)가 읽기 동작을 바꾸지 않음을 검증한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/product",
            "spring.flyway.schemas=product",
            "spring.flyway.default-schema=product"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DefaultCategoryReader.class, TransactionalCategoryReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CategoryPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final CategoryReader categoryReader;
    private final CategoryRepository categoryRepository;

    CategoryPersistenceTest(CategoryReader categoryReader, CategoryRepository categoryRepository) {
        this.categoryReader = categoryReader;
        this.categoryRepository = categoryRepository;
    }

    @Test
    @DisplayName("활성 카테고리를 이름순으로 조회하고 삭제분은 제외한다 — validate 스키마 정합")
    void getCategoriesOrderedByNameExcludingDeleted() {
        categoryRepository.save(Category.create("의류"));
        categoryRepository.save(Category.create("가전"));
        Category deleted = categoryRepository.save(Category.create("삭제예정"));
        deleted.delete(java.time.Instant.now());
        categoryRepository.save(deleted);

        List<CategoryInfo> categories = categoryReader.getCategories();

        assertThat(categories).extracting(CategoryInfo::name).containsExactly("가전", "의류");
    }
}
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `./gradlew :module-domains:domain-product:test --tests CategoryPersistenceTest`
Expected: FAIL — `TransactionalCategoryReader`가 없어 컴파일 오류(`DefaultCategoryReader`는 아직 기존 시그니처).

- [ ] **Step 4: TransactionalCategoryReader 작성**

```java
package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.required.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DefaultCategoryReader}의 캐시 미스 경로 위임 서비스다.
 *
 * <p>provided 계약을 만들지 않는다 — 컨텍스트 안에 {@code CategoryReader} 구현체가
 * {@link DefaultCategoryReader} 하나만 존재하도록 유지한다.
 */
@Service
class TransactionalCategoryReader {

    private final CategoryRepository categoryRepository;

    TransactionalCategoryReader(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    List<CategoryInfo> getCategories() {
        return categoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryInfo::from)
                .toList();
    }
}
```

- [ ] **Step 5: DefaultCategoryReader 수정**

```java
package com.commerce.domain.product.application;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.provided.CategoryReader;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** {@link CategoryReader}의 기본 구현이다. */
@Service
class DefaultCategoryReader implements CategoryReader {

    private final TransactionalCategoryReader transactionalCategoryReader;

    DefaultCategoryReader(TransactionalCategoryReader transactionalCategoryReader) {
        this.transactionalCategoryReader = transactionalCategoryReader;
    }

    @Cacheable(cacheNames = CategoryCacheNames.CATEGORY, key = "'all'")
    @Override
    public List<CategoryInfo> getCategories() {
        return transactionalCategoryReader.getCategories();
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :module-domains:domain-product:test --tests CategoryPersistenceTest`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add module-domains/domain-product
git commit -m "feat: CategoryReader 캐시 분리(DefaultCategoryReader·TransactionalCategoryReader)"
```

---

### Task 5: domain-product — 쓰기 서비스 evict 배선

**Files:**
- Modify: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/DefaultCategoryAppender.java`
- Modify: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/DefaultCategoryModifier.java`
- Modify: `module-domains/domain-product/src/main/java/com/commerce/domain/product/application/DefaultCategoryRemover.java`

**Interfaces:**
- Consumes: `CategoryCacheNames.CATEGORY`(Task 4).
- Produces: (evict 배선 자체 — Task 9 통합 테스트가 실제 무효화를 검증한다. 이 태스크 단독으로는 행동 테스트를 추가하지 않는다 — 같은 행동을 상위 레벨(Task 9)에서 검증하므로 반복하지 않는다, testing.md의 테스트 레벨 규칙.)

- [ ] **Step 1: DefaultCategoryAppender에 evict 추가**

`module-domains/domain-product/src/main/java/com/commerce/domain/product/application/DefaultCategoryAppender.java`를 연다. import에 `com.commerce.domain.product.application.provided.CategoryCacheNames`와 `org.springframework.cache.annotation.CacheEvict`를 추가하고, `create` 메서드에 애노테이션을 붙인다.

```java
package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.CategoryAppender;
import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.Category;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryAppender}의 기본 구현이다. */
@Service
class DefaultCategoryAppender implements CategoryAppender {

    private final CategoryRepository categoryRepository;

    DefaultCategoryAppender(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @CacheEvict(cacheNames = CategoryCacheNames.CATEGORY, allEntries = true)
    @Transactional
    @Override
    public UUID create(String name) {
        return categoryRepository.save(Category.create(name)).getId();
    }
}
```

- [ ] **Step 2: DefaultCategoryModifier에 evict 추가**

```java
package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.provided.CategoryModifier;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.exception.CategoryNotFoundException;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryModifier}의 기본 구현이다. */
@Service
class DefaultCategoryModifier implements CategoryModifier {

    private final CategoryRepository categoryRepository;

    DefaultCategoryModifier(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @CacheEvict(cacheNames = CategoryCacheNames.CATEGORY, allEntries = true)
    @Transactional
    @Override
    public void rename(UUID categoryId, String newName) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .rename(newName);
    }
}
```

- [ ] **Step 3: DefaultCategoryRemover에 evict 추가**

```java
package com.commerce.domain.product.application;

import com.commerce.domain.product.application.provided.CategoryCacheNames;
import com.commerce.domain.product.application.provided.CategoryRemover;
import com.commerce.domain.product.application.required.CategoryRepository;
import com.commerce.domain.product.domain.exception.CategoryNotFoundException;
import com.commerce.domain.product.domain.exception.ProductErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CategoryRemover}의 기본 구현이다. */
@Service
class DefaultCategoryRemover implements CategoryRemover {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    DefaultCategoryRemover(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = CategoryCacheNames.CATEGORY, allEntries = true)
    @Transactional
    @Override
    public void delete(UUID categoryId) {
        categoryRepository
                .findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(ProductErrorCode.CATEGORY_NOT_FOUND))
                .delete(clock.instant());
    }
}
```

- [ ] **Step 4: 도메인 모듈 전체 컴파일·테스트 확인**

Run: `./gradlew :module-domains:domain-product:test`
Expected: BUILD SUCCESSFUL — 기존 테스트 전부 여전히 통과(evict는 아직 아키텍처 테스트·통합 테스트가 없어 이 시점엔 동작 검증되지 않는다 — Task 9에서 검증).

- [ ] **Step 5: 커밋**

```bash
git add module-domains/domain-product
git commit -m "feat: Category 쓰기 서비스에 캐시 evict 배선"
```

---

### Task 6: 아키텍처 테스트 — 캐시 불변식 강제

**Files:**
- Modify: `module-tests/test-architecture/src/test/java/com/commerce/test/architecture/ArchitectureTest.java`

**Interfaces:**
- Consumes: Task 4·5의 `@Cacheable`/`@CacheEvict`/`CategoryCacheNames`(현재 유일한 실사례 — 규칙 자체는 이름을 하드코딩하지 않고 `com.commerce` 전체를 스캔해 범용으로 동작한다).

이 태스크는 기존 파일 맨 끝(마지막 `@Test` 메서드 다음, 클래스 닫는 `}` 앞)에 새 `@Test` 메서드들을 추가한다. 기존 임포트에 아래를 더한다(파일 상단 import 블록에 알파벳 순으로 삽입).

```java
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
```

- [ ] **Step 1: 헬퍼 필드·메서드 추가**

클래스 상단(기존 `BASE_FINDERS` 근처)에 캐시 애노테이션이 붙은 메서드를 한 번만 스캔하는 헬�퍼를 추가한다.

```java
    // 캐시 애노테이션이 붙은 메서드 전수 — 아래 캐시 불변식 테스트들이 공유한다.
    private static final List<JavaMethod> CACHEABLE_METHODS = CLASSES.stream()
            .flatMap(clazz -> clazz.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(Cacheable.class))
            .toList();

    private static final List<JavaMethod> CACHE_EVICT_METHODS = CLASSES.stream()
            .flatMap(clazz -> clazz.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(CacheEvict.class))
            .toList();

    // {도메인}:{대상}:v{n} 또는 query:{모듈}:{대상}:v{n} — caching.md 이름·키·버전.
    private static final Pattern CACHE_NAME_PATTERN =
            Pattern.compile("^(query:[a-z]+:[a-z]+:v\\d+|[a-z]+:[a-z]+:v\\d+)$");

    private static String cacheableName(JavaMethod method) {
        return method.getAnnotationOfType(Cacheable.class).cacheNames()[0];
    }

    private static String cacheEvictName(JavaMethod method) {
        return method.getAnnotationOfType(CacheEvict.class).cacheNames()[0];
    }
```

- [ ] **Step 2: 위치·계층 제한 테스트**

```java
    @Test
    @DisplayName("@Cacheable은 도메인 application의 provided 구현 또는 query 모듈 구현에만 선언된다")
    void cacheableOnlyOnDomainProvidedImplOrQueryModule() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            String packageName = method.getOwner().getPackageName();
            boolean inDomainApplication = packageName.matches("com\\.commerce\\.domain\\.[a-z]+\\.application")
                    || packageName.matches("com\\.commerce\\.domain\\.[a-z]+\\.application\\.provided");
            boolean inQueryModule = packageName.startsWith("com.commerce.query.");
            assertTrue(
                    inDomainApplication || inQueryModule,
                    "@Cacheable이 허용 계층 밖에 있다: " + method.getFullName());
        }
    }

    @Test
    @DisplayName("@CacheEvict는 같은 모듈의 쓰기 서비스(Appender·Modifier·Remover·Processor)에만 선언된다")
    void cacheEvictOnlySameModuleWriteServices() {
        for (JavaMethod method : CACHE_EVICT_METHODS) {
            String className = method.getOwner().getSimpleName();
            assertTrue(
                    className.matches("Default(.*)(Appender|Modifier|Remover|Processor)"),
                    "@CacheEvict가 쓰기 서비스 밖에 있다: " + method.getFullName());
        }
    }
```

- [ ] **Step 3: 트랜잭션 분리·null·Page 반환 금지 테스트**

```java
    @Test
    @DisplayName("@Cacheable 메서드·클래스는 @Transactional을 동시 선언하지 않는다")
    void cacheableMethodsNotTransactional() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            boolean methodLevel = method.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);
            boolean classLevel = method.getOwner()
                    .isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);
            assertTrue(
                    !methodLevel && !classLevel,
                    "@Cacheable 메서드가 @Transactional과 같이 선언됐다(클래스 레벨 포함): " + method.getFullName());
        }
    }

    @Test
    @DisplayName("@Cacheable 메서드는 @Nullable 반환을 선언하지 않는다")
    void cacheableMethodsNotNullableReturn() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            assertTrue(
                    !method.isAnnotatedWith(org.jspecify.annotations.Nullable.class),
                    "@Cacheable 메서드가 @Nullable 반환을 선언했다: " + method.getFullName());
        }
    }

    @Test
    @DisplayName("@Cacheable 반환 타입은 캐시 값 허용 타입(Info·컬렉션·표준 스칼라)이고 Page·Slice가 아니다")
    void cacheableMethodsReturnAllowedValueTypes() {
        var forbiddenRawTypes =
                java.util.Set.of("org.springframework.data.domain.Page", "org.springframework.data.domain.Slice");
        var scalarRawTypes = java.util.Set.of(
                "java.lang.String",
                "java.lang.Boolean",
                "boolean",
                "java.lang.Long",
                "long",
                "java.lang.Integer",
                "int",
                "java.util.UUID",
                "java.time.Instant");
        var collectionRawTypes = java.util.Set.of("java.util.List", "java.util.Collection", "java.util.Set");

        for (JavaMethod method : CACHEABLE_METHODS) {
            String rawType = method.getRawReturnType().getFullName();
            assertTrue(!forbiddenRawTypes.contains(rawType), "@Cacheable이 Page/Slice를 직접 반환한다: " + method.getFullName());

            boolean isInfoType = method.getRawReturnType().getSimpleName().endsWith("Info");
            boolean isScalar = scalarRawTypes.contains(rawType);
            boolean isCollectionOfInfo = collectionRawTypes.contains(rawType)
                    && method.getReturnType() instanceof com.tngtech.archunit.core.domain.JavaParameterizedType parameterized
                    && parameterized.getActualTypeArguments().stream()
                            .allMatch(arg -> arg.toErasure().getSimpleName().endsWith("Info"));

            assertTrue(
                    isInfoType || isScalar || isCollectionOfInfo,
                    "@Cacheable 반환 타입이 허용 캐시 값 타입이 아니다(Info·컬렉션<Info>·표준 스칼라만 허용): "
                            + method.getFullName() + " -> " + rawType);
        }
    }
```

- [ ] **Step 4: 금지 속성·evict 짝 존재 테스트**

```java
    @Test
    @DisplayName("@CachePut·condition·unless·beforeInvocation은 어디에도 쓰지 않는다")
    void forbiddenCacheAttributesNotUsed() {
        boolean anyCachePut = CLASSES.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .anyMatch(method -> method.isAnnotatedWith(org.springframework.cache.annotation.CachePut.class));
        assertTrue(!anyCachePut, "@CachePut이 사용됐다 — caching.md 금지 목록");

        for (JavaMethod method : CACHEABLE_METHODS) {
            Cacheable annotation = method.getAnnotationOfType(Cacheable.class);
            assertTrue(annotation.condition().isEmpty(), "@Cacheable condition이 쓰였다: " + method.getFullName());
            assertTrue(annotation.unless().isEmpty(), "@Cacheable unless가 쓰였다: " + method.getFullName());
        }
        for (JavaMethod method : CACHE_EVICT_METHODS) {
            CacheEvict annotation = method.getAnnotationOfType(CacheEvict.class);
            assertTrue(
                    !annotation.beforeInvocation(), "@CacheEvict beforeInvocation이 쓰였다: " + method.getFullName());
        }
    }

    @Test
    @DisplayName("@Cacheable 캐시 이름마다 같은 모듈에 동명 @CacheEvict가 있다(query TTL-only 제외)")
    void everyCacheableHasMatchingEvictInSameModule() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            if (method.getOwner().getPackageName().startsWith("com.commerce.query.")) {
                continue; // TTL-only — evict 짝 제외
            }
            String name = cacheableName(method);
            boolean hasEvict = CACHE_EVICT_METHODS.stream()
                    .anyMatch(evict -> cacheEvictName(evict).equals(name)
                            && sameDomainModule(evict.getOwner(), method.getOwner()));
            assertTrue(hasEvict, "@Cacheable 캐시 이름에 짝이 되는 @CacheEvict가 없다: " + name);
        }
    }

    @Test
    @DisplayName("@CacheEvict 캐시 이름마다 같은 모듈에 동명 @Cacheable이 있다(고아 evict 금지)")
    void everyCacheEvictHasMatchingCacheableInSameModule() {
        for (JavaMethod method : CACHE_EVICT_METHODS) {
            String name = cacheEvictName(method);
            boolean hasCacheable = CACHEABLE_METHODS.stream()
                    .anyMatch(cacheable -> cacheableName(cacheable).equals(name)
                            && sameDomainModule(cacheable.getOwner(), method.getOwner()));
            assertTrue(hasCacheable, "@CacheEvict가 고아다(대응 @Cacheable 없음): " + name);
        }
    }

    private static boolean sameDomainModule(JavaClass a, JavaClass b) {
        return domainModuleOf(a).equals(domainModuleOf(b));
    }

    private static String domainModuleOf(JavaClass clazz) {
        String[] segments = clazz.getPackageName().split("\\.");
        // com.commerce.domain.{name}.application... → segments[2] == "domain", segments[3] == {name}
        return segments.length > 3 ? segments[2] + "." + segments[3] : clazz.getPackageName();
    }
```

- [ ] **Step 5: 이름 형식·키 명시·버전 공존 금지 테스트**

```java
    @Test
    @DisplayName("캐시 이름은 {도메인}:{대상}:v{n} 또는 query:{모듈}:{대상}:v{n} 형식이다")
    void cacheNamesMatchNamingFormat() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            assertTrue(
                    CACHE_NAME_PATTERN.matcher(cacheableName(method)).matches(),
                    "캐시 이름 형식 위반: " + cacheableName(method));
        }
    }

    @Test
    @DisplayName("@Cacheable과 키 지정 @CacheEvict는 key를 명시한다(allEntries=true evict는 제외)")
    void cacheableAndKeyedEvictDeclareExplicitKey() {
        for (JavaMethod method : CACHEABLE_METHODS) {
            assertTrue(
                    !method.getAnnotationOfType(Cacheable.class).key().isEmpty(),
                    "@Cacheable이 key를 명시하지 않았다: " + method.getFullName());
        }
        for (JavaMethod method : CACHE_EVICT_METHODS) {
            CacheEvict annotation = method.getAnnotationOfType(CacheEvict.class);
            if (annotation.allEntries()) {
                continue;
            }
            assertTrue(!annotation.key().isEmpty(), "키 지정 @CacheEvict가 key를 명시하지 않았다: " + method.getFullName());
        }
    }

    @Test
    @DisplayName("같은 모듈에서 버전만 다른 같은 캐시 이름은 공존하지 않는다")
    void noVersionOnlyDuplicateNamesInSameModule() {
        var byModuleAndBase = new java.util.HashMap<String, java.util.Set<String>>();
        for (JavaMethod method : CACHEABLE_METHODS) {
            String name = cacheableName(method);
            String base = name.replaceAll(":v\\d+$", "");
            String key = domainModuleOf(method.getOwner()) + "::" + base;
            byModuleAndBase.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(name);
        }
        byModuleAndBase.forEach((key, names) -> assertTrue(
                names.size() <= 1, "같은 모듈에 버전만 다른 같은 이름이 공존한다: " + key + " -> " + names));
    }
```

- [ ] **Step 6: 이름 상수 일치·값 타입 테스트**

```java
    @Test
    @DisplayName("애노테이션 캐시 이름 값 전수는 이름 상수 클래스가 선언한 값과 일치한다")
    void annotationCacheNamesMatchDeclaredConstants() {
        java.util.Set<String> declaredConstants = CLASSES.stream()
                .filter(clazz -> clazz.getSimpleName().endsWith("CacheNames"))
                .flatMap(clazz -> clazz.getFields().stream())
                .filter(field -> field.getRawType().getFullName().equals("java.lang.String"))
                .map(field -> {
                    try {
                        return (String)
                                Class.forName(field.getOwner().getFullName()).getField(field.getName()).get(null);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(java.util.stream.Collectors.toSet());

        java.util.Set<String> usedNames = new java.util.HashSet<>();
        CACHEABLE_METHODS.forEach(method -> usedNames.add(cacheableName(method)));
        CACHE_EVICT_METHODS.forEach(method -> usedNames.add(cacheEvictName(method)));

        assertTrue(
                declaredConstants.containsAll(usedNames),
                "애노테이션이 이름 상수에 없는 값을 쓴다: " + usedNames + " vs " + declaredConstants);
    }
```

- [ ] **Step 7: 쓰기 서비스·Validator의 같은 모듈 @Cacheable 직접 호출 금지 테스트**

```java
    @Test
    @DisplayName("쓰기 서비스·Validator는 같은 모듈의 @Cacheable 메서드를 직접 호출하지 않는다")
    void writeServicesDoNotCallSameModuleCacheableMethodsDirectly() {
        var cacheableOwnersByModule = new java.util.HashMap<String, java.util.Set<JavaClass>>();
        for (JavaMethod method : CACHEABLE_METHODS) {
            cacheableOwnersByModule
                    .computeIfAbsent(domainModuleOf(method.getOwner()), m -> new java.util.HashSet<>())
                    .add(method.getOwner());
        }

        List<String> violations = new ArrayList<>();
        CLASSES.stream()
                .filter(clazz -> clazz.getSimpleName().matches("Default(.*)(Appender|Modifier|Remover|Processor)")
                        || clazz.getSimpleName().endsWith("Validator"))
                .forEach(writeService -> {
                    String module = domainModuleOf(writeService);
                    java.util.Set<JavaClass> cacheableOwners = cacheableOwnersByModule.getOrDefault(module, java.util.Set.of());
                    writeService.getMethodCallsFromSelf().forEach(call -> {
                        if (cacheableOwners.contains(call.getTargetOwner())) {
                            violations.add(writeService.getFullName() + " 가 같은 모듈의 @Cacheable 메서드를 직접 호출한다: "
                                    + call.getTargetOwner().getFullName() + "." + call.getTarget().getName());
                        }
                    });
                });
        assertEquals(List.of(), violations);
    }
```

- [ ] **Step 8: 의존 그래프 기반 — 캐시 애노테이션 모듈 임베드 앱의 캐시 구성 동반 테스트**

기존 `appDomainEmbeddings()` 헬퍼(`/app-domain-embeddings.properties` 리소스 기반, 이벤트 소비 커버리지 테스트가 이미 쓰는 것과 동일)를 재사용한다. `app-migration`은 런타임 전용 의존 앱이라 제외한다(실제로 `domain-product`를 `runtimeOnly`로 임베드하지만 캐시 구성 대상이 아니다).

```java
    @Test
    @DisplayName("@Cacheable 가진 도메인을 임베드하는 실행 앱은 캐시 구성(@EnableCaching)을 동반한다(런타임 전용 의존 앱 제외)")
    void appsEmbeddingCacheableDomainsCarryCacheConfig() {
        java.util.Set<String> cachedDomainModules = CACHEABLE_METHODS.stream()
                .map(method -> domainModuleOf(method.getOwner()).replaceFirst("^domain\\.", ""))
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Set<String>> embeddedDomainsByApp = appDomainEmbeddings();
        List<String> violations = new ArrayList<>();
        embeddedDomainsByApp.forEach((app, embedded) -> {
            if (app.equals("app-migration")) {
                return; // 런타임 전용 의존 앱 — 캐시 구성 대상 아님
            }
            boolean embedsCachedDomain = embedded.stream().anyMatch(cachedDomainModules::contains);
            if (!embedsCachedDomain) {
                return;
            }
            String appBasePackage = "com.commerce.app." + app.replaceFirst("^app-", "");
            boolean hasEnableCaching = CLASSES.stream()
                    .filter(clazz -> clazz.getPackageName().startsWith(appBasePackage))
                    .anyMatch(clazz -> clazz.isAnnotatedWith(org.springframework.cache.annotation.EnableCaching.class));
            if (!hasEnableCaching) {
                violations.add(app + " 가 캐시 애노테이션 있는 도메인을 임베드하지만 @EnableCaching 구성이 없다");
            }
        });
        assertEquals(List.of(), violations);
    }
```

- [ ] **Step 9: 전체 아키텍처 테스트 실행**

Run: `./gradlew :module-tests:test-architecture:test`
Expected: BUILD SUCCESSFUL — 새 테스트 12개 전부 통과(Task 4·5·7·8이 이미 규칙을 만족하는 형태로 구현됐으므로 즉시 통과가 정상이다 — testing.md "이미 존재하는 행동의 시나리오 보강 테스트는 즉시 통과가 정상이다"에 준한다). Step 8의 테스트는 Task 7·8(app-api·app-admin의 `CacheConfig`에 `@EnableCaching`)이 끝난 뒤에야 통과한다 — 그 전에는 이 태스크 실행 시점이 Task 6이므로 FAIL이 정상이고, Task 7·8 완료 후 재실행해 PASS를 확인한다.

- [ ] **Step 10: 커밋**

```bash
git add module-tests/test-architecture
git commit -m "test: 캐시 애노테이션 불변식 아키텍처 테스트 추가"
```

---

### Task 7: app-api 캐시 배선

**Files:**
- Modify: `module-apps/app-api/build.gradle.kts`
- Create: `module-apps/app-api/src/main/java/com/commerce/app/api/config/CacheConfig.java`

**Interfaces:**
- Consumes: `common-cache`의 `CacheRegistration`·`RedisCacheManagerFactory`·`EvictSafeCacheManager`(Task 1-3), `CategoryCacheNames`·`CategoryInfo`(Task 4).

- [ ] **Step 1: build.gradle.kts 수정**

`implementation(project(":module-common:common-web"))` 다음 줄에 추가한다.

```kotlin
    implementation(project(":module-common:common-cache"))
```

기존 `implementation(libs.spring.boot.starter.actuator)` 다음 줄에 추가한다(캐시 전용 커넥션을 메인 코드에서 직접 구성하므로 테스트 전용이던 의존을 메인으로 승격한다).

```kotlin
    implementation(libs.spring.boot.starter.data.redis)
```

수정 후 `testImplementation(libs.spring.boot.starter.data.redis)` 줄은 중복이므로 삭제한다(메인 `implementation`이 테스트 클래스패스에도 전이된다).

- [ ] **Step 2: CacheConfig 작성**

```java
package com.commerce.app.api.config;

import com.commerce.common.cache.CacheRegistration;
import com.commerce.common.cache.EvictSafeCacheManager;
import com.commerce.common.cache.RedisCacheManagerFactory;
import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryCacheNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.type.TypeFactory;

/**
 * 캐시 전용 Redis 커넥션·CacheManager를 구성한다.
 *
 * <p>기존 공유 Redis 커넥션(멱등 키·레이트리밋·리프레시 토큰·스케줄 락)과 분리된 전용 커넥션을 쓴다 —
 * command timeout(300ms)을 캐시 전용으로 짧게 잡아 장애 시 빠르게 미스로 강등하기 위함이다.
 *
 * <p>이 커넥션은 별도 {@code @Bean}으로 노출하지 않고 {@link #cacheManager()} 안에서 지역 변수로만
 * 만든다 — Spring Boot의 기본 {@code RedisConnectionFactory} 자동 구성(
 * {@code LettuceConnectionConfiguration.redisConnectionFactory}, {@code @ConditionalOnMissingBean(
 * RedisConnectionFactory.class)})은 타입 기준으로 미존재를 판정하므로, 이 타입의 빈을 하나라도 더
 * 등록하면 자격·타입과 무관하게 자동 구성이 통째로 백오프한다. 그러면 기존 공유 커넥션(멱등 키·
 * 리프레시 토큰·레이트리밋·스케줄 락이 쓰는 {@code StringRedisTemplate}·{@code RedisConnectionFactory})이
 * 조용히 이 캐시 전용(300ms 타임아웃) 커넥션으로 바뀌어 버린다 — 별도 빈으로 노출하지 않으면 이 문제
 * 자체가 생기지 않는다.
 */
@Configuration
@EnableCaching
class CacheConfig implements CachingConfigurer {

    private final String redisHost;
    private final int redisPort;
    private final MeterRegistry meterRegistry;

    CacheConfig(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort,
            MeterRegistry meterRegistry) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Bean
    public CacheManager cacheManager() {
        LettuceClientConfiguration clientConfiguration =
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build();
        LettuceConnectionFactory cacheRedisConnectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort), clientConfiguration);
        // 컨테이너가 관리하는 빈이 아니라 지역 변수라 생명주기 콜백을 직접 호출해야 한다.
        cacheRedisConnectionFactory.afterPropertiesSet();

        List<CacheRegistration> registrations = List.of(new CacheRegistration(
                CategoryCacheNames.CATEGORY,
                Duration.ofMinutes(10),
                TypeFactory.createDefaultInstance().constructCollectionType(List.class, CategoryInfo.class)));
        RedisCacheManager redisCacheManager = RedisCacheManagerFactory.build(cacheRedisConnectionFactory, registrations);
        // RedisCacheManager도 InitializingBean이지만 @Bean 메서드가 반환하는 건 EvictSafeCacheManager
        // 쪽이라 컨테이너가 이 내부 인스턴스의 생명주기를 대신 관리해 주지 않는다 — 직접 호출해야
        // getCacheConfigurations()·getCache(name)이 정상 동작한다.
        redisCacheManager.afterPropertiesSet();
        return new EvictSafeCacheManager(redisCacheManager, meterRegistry);
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
```

- [ ] **Step 2b: 캐시 전용 커넥션이 별도 빈으로 노출되지 않았는지, 헬스 인디케이터를 달지 않았는지 확인**

`CacheConfig.java`에 `LettuceConnectionFactory`(또는 다른 `RedisConnectionFactory` 구현)를 반환하는 `@Bean` 메서드가 없음을 육안 확인한다 — 있으면 Spring Boot의 기본 `RedisConnectionFactory` 자동 구성이 백오프해 기존 공유 Redis 사용처(멱등 키·리프레시 토큰·레이트리밋·스케줄 락)가 캐시 전용(300ms 타임아웃) 커넥션으로 조용히 바뀐다. 캐시 전용 커넥션은 `cacheManager()` 메서드 안의 지역 변수여야 한다. `HealthIndicator`도 등록하지 않았는지 확인한다(등록하면 미스 강등 설계와 모순되는 readiness DOWN 전파가 생긴다).

- [ ] **Step 3: 앱 부팅 확인**

Run: `./gradlew :module-apps:app-api:test`
Expected: BUILD SUCCESSFUL — 기존 통합 테스트(`WebIntegrationTest`·`FacadeIntegrationTest` 등)가 새 `@EnableCaching`·`CacheConfig` 빈 추가에도 컨텍스트 로딩에 실패하지 않고 통과한다.

- [ ] **Step 4: 커밋**

```bash
git add module-apps/app-api
git commit -m "feat: app-api에 캐시 전용 Redis 커넥션·CacheManager 배선"
```

---

### Task 8: app-admin 캐시 배선

**Files:**
- Modify: `module-apps/app-admin/build.gradle.kts`
- Create: `module-apps/app-admin/src/main/java/com/commerce/app/admin/config/CacheConfig.java`

**Interfaces:**
- Consumes: Task 7과 동일(common-cache·CategoryCacheNames·CategoryInfo).

- [ ] **Step 1: build.gradle.kts 수정**

Task 7 Step 1과 동일한 변경을 app-admin에 적용한다 — `implementation(project(":module-common:common-cache"))` 추가, `implementation(libs.spring.boot.starter.data.redis)`를 메인으로 추가(app-admin은 현재 `testImplementation`에도 이 좌표가 없으므로 신규 추가만 하면 된다 — 기존 build.gradle.kts를 확인해 test 쪽 중복 여부만 점검).

- [ ] **Step 2: CacheConfig 작성**

Task 7의 `CacheConfig.java`와 완전히 동일한 내용을 `package com.commerce.app.admin.config;`로만 바꿔 작성한다(파일 경로: `module-apps/app-admin/src/main/java/com/commerce/app/admin/config/CacheConfig.java`).

- [ ] **Step 3: 앱 부팅 확인**

Run: `./gradlew :module-apps:app-admin:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add module-apps/app-admin
git commit -m "feat: app-admin에 캐시 전용 Redis 커넥션·CacheManager 배선"
```

---

### Task 9: 통합 테스트 — 캐시 무효화 대표 시나리오

**시나리오 문장(사람 합의 대상, testing.md 시나리오 소유):**

> 카테고리 이름 변경 커밋 후 카테고리 목록 조회는 캐시된 옛 이름이 아니라 변경된 이름을 즉시 반환한다.

이 문장은 구현 전 사람 합의가 필요하다(`docs/testing.md`의 시나리오 소유 절차). 이 태스크를 실행하는 에이전트는 시작 전에 이 문장을 사람에게 제시하고 승인을 받는다 — 이미 이 계획의 상위 브레인스토밍 단계에서 같은 취지로 합의됐다면 재확인만 한다.

**Files:**
- Create: `module-apps/app-admin/src/test/java/com/commerce/app/admin/facade/CategoryCacheInvalidationTest.java`

**Interfaces:**
- Consumes: `CategoryReader`·`CategoryAppender`·`CategoryModifier`(domain-product provided), `FacadeIntegrationTest`(app-admin 기존 통합 테스트 하네스, `SharedPostgresContainer`·`SharedRedisContainer` 배선).

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`FacadeIntegrationTest`를 상속해 `SharedPostgresContainer`·`SharedRedisContainer` 배선을 재사용한다. `@Transactional`이 아니므로(부모 클래스가 롤백을 걸지 않음) 실제 커밋이 일어나 evict가 정상 동작한다.

```java
package com.commerce.app.admin.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.product.application.info.CategoryInfo;
import com.commerce.domain.product.application.provided.CategoryAppender;
import com.commerce.domain.product.application.provided.CategoryModifier;
import com.commerce.domain.product.application.provided.CategoryReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 카테고리 캐시(product:category:v1) 무효화 대표 시나리오를 검증한다. */
class CategoryCacheInvalidationTest extends FacadeIntegrationTest {

    @Autowired
    private CategoryReader categoryReader;

    @Autowired
    private CategoryAppender categoryAppender;

    @Autowired
    private CategoryModifier categoryModifier;

    @Test
    @DisplayName("카테고리 이름 변경 커밋 후 목록 조회는 캐시된 옛 이름이 아니라 변경된 이름을 즉시 반환한다")
    void renameEvictsCacheAndReflectsNewNameImmediately() {
        UUID categoryId = categoryAppender.create("가전-옛이름-" + UUID.randomUUID());
        List<CategoryInfo> beforeRename = categoryReader.getCategories();
        assertThat(beforeRename).anyMatch(category -> category.id().equals(categoryId));

        String newName = "가전-새이름-" + UUID.randomUUID();
        categoryModifier.rename(categoryId, newName);

        List<CategoryInfo> afterRename = categoryReader.getCategories();
        assertThat(afterRename)
                .filteredOn(category -> category.id().equals(categoryId))
                .extracting(CategoryInfo::name)
                .containsExactly(newName);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :module-apps:app-admin:test --tests CategoryCacheInvalidationTest`
Expected: 이 시점엔 Task 4·5·8이 이미 완료돼 있어 실제로는 PASS할 가능성이 높다(evict·캐시 배선이 전부 이미 구현됨). 만약 PASS라면 Step 3(실패 확인) 없이 바로 다음 태스크로 진행 가능 — testing.md "이미 존재하는 행동의 시나리오 보강 테스트는 즉시 통과가 정상이다, 사전 실패 확인을 요구하지 않는다"에 해당한다.

- [ ] **Step 3: (선택) evict를 잠시 제거해 테스트가 실패를 잡아내는지 확인**

`DefaultCategoryModifier.rename()`에서 `@CacheEvict` 애노테이션을 임시로 지우고 같은 테스트를 실행해 FAIL(캐시된 옛 이름 반환)함을 확인한다. 확인 후 애노테이션을 원복한다. 이 단계는 테스트가 실제로 회귀를 잡아내는지 검증하는 안전장치다.

Run: `./gradlew :module-apps:app-admin:test --tests CategoryCacheInvalidationTest`
Expected(애노테이션 제거 상태): FAIL — `afterRename`이 옛 이름을 반환.

애노테이션 원복 후 재실행:

Run: `./gradlew :module-apps:app-admin:test --tests CategoryCacheInvalidationTest`
Expected: PASS.

- [ ] **Step 4: 커밋**

```bash
git add module-apps/app-admin
git commit -m "test: 카테고리 캐시 무효화 대표 시나리오 통합 테스트 추가"
```

---

### Task 10: 문서 반영 — domain_model.md 캐시 표

**Files:**
- Modify: `docs/project/domain_model.md`

**Interfaces:**
- (문서 변경만, 코드 인터페이스 없음)

- [ ] **Step 1: 캐시 섹션 추가**

파일 끝(`## 영속·마이그레이션 유의` 섹션 다음)에 새 최상위 섹션을 추가한다.

```markdown

## 캐시

| 이름 | 대상 조회 | TTL | 로컬 채택 예외 | 사유 |
|---|---|---|---|---|
| `product:category:v1` | `CategoryReader.getCategories()` | 10분 | 없음(Redis) | 저변경·소량 데이터, 관리자 변경 빈도 낮고 스토어프론트 조회 빈도 높음 |
```

- [ ] **Step 2: 커밋**

```bash
git add docs/project/domain_model.md
git commit -m "docs: 캐시 합의 목록 표 추가(product:category:v1)"
```

---

## 최종 검증 (전체 완료 후 1회)

- [ ] Run: `./gradlew build`
- [ ] Expected: BUILD SUCCESSFUL — 전 모듈 컴파일·테스트·아키텍처 테스트·Spotless/NullAway/Error Prone 게이트 통과.
- [ ] `docs/caching.md`의 "완료 판정" 체크리스트를 스펙 문서(`docs/superpowers/specs/2026-07-24-product-category-cache-design.md`)의 "완료 판정" 절과 대조해 전 항목 충족을 확인한다.

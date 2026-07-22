# Entity & Persistence

## 언제

- `domain` 구역에 `@Entity`를 만들거나 필드·상태 enum을 추가·수정할 때
- 엔티티의 `@Id`, 버전 컬럼, 논리 참조를 설계할 때
- 애그리거트 내부 연관과 자식 엔티티의 캐스케이드 생명주기를 설계할 때


## 규칙

### 엔티티 골격

- `BaseTimeEntity`를 상속한다.
- `createdAt`, `updatedAt`은 JPA Auditing이 채운다. 시각 필드를 직접 선언하지 않는다.
- `@CreatedBy`, `@LastModifiedBy`는 필요한 도메인만 opt-in한다.
- 영속 필드의 매핑 애노테이션·`@Nullable` 표기와 초기화 검사 기준은 → [code-quality](code-quality.md)의 NullAway.
- `BaseTimeEntity`는 `Persistable`을 구현해 merge penalty를 방어한다.
- 수동 `@Id` 엔티티를 JPA가 detached로 오해하지 않도록 `createdAt == null`이면 신규로 판정해 `persist()`로 처리한다.
- 이를 통해 `save()` 시 발생할 수 있는 불필요한 `SELECT`를 방지한다.
- 수정은 dirty checking으로만 처리한다.
- 관리 엔티티에 `save()`를 호출하지 않는다.
- 기존 ID로 엔티티를 새로 조립해 `save()`하면 `createdAt == null`로 신규 엔티티로 판정되어 flush 시 PK 중복 예외가 발생한다.
- 수정은 반드시 리포지토리로 조회한 관리 엔티티에서 수행한다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity<ID extends Serializable> implements Persistable<ID> {

    @CreatedDate
    @Column(updatable = false) // @Column 제외 대상
    private Instant createdAt;

    @LastModifiedDate
    @Column // Auditing이 insert 시에도 채운다
    private Instant updatedAt;

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() {
        return this.createdAt == null;
    }

    @Override
    public abstract ID getId(); // final 금지

    // 식별자 기반 동등성
    // Set, LAZY 프록시, detached 엔티티 비교에서 안전해야 한다.
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseTimeEntity<?> that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return getId() == null ? 0 : getId().hashCode();
    }
}
```


### 엔티티 ID

- `@Id`는 `create()` 팩토리에서 `UuidV7Generator.generate()`로 생성한다.
- `@GeneratedValue`를 사용하지 않는다.
- ID는 DB 왕복 없이 애플리케이션에서 확정한다.
- 응답에는 UUIDv7을 문자열로 반환한다.


### 값 객체 매핑

| 대상 | 매핑 방식 | 비고 |
|---|---|---|
| 단일 값 | `AttributeConverter` + `@Convert` | 값 하나 |
| 다중 값 | `@Embeddable` record + `@Embedded` | 컬럼별 조회·인덱싱 가능 |
| 판별 유니온 | 단일 `@Embeddable` record 평탄화 | enum + nullable 컬럼 |

- `@Embeddable` record의 compact constructor 검증은 조회 시마다 실행된다.
- 레거시 데이터가 현재 불변식을 어기면 조회 시 예외가 발생한다.
- 형·필드 정합성은 compact constructor가 강제한다.
- 판별 유니온 VO의 형↔필드 정합성을 DB까지 강제해야 하면 테이블 레벨 CHECK 제약을 추가한다.
- sealed 계층은 임베더블 다형성 미지원 때문에 직접 매핑하지 않는다.
- `@Inheritance`로 값을 엔티티로 승격하지 않는다.


### 상태 전이

- 상태 enum은 `@Enumerated(EnumType.STRING)`으로 매핑한다.
- ordinal 매핑을 금지한다.
- 상태 변경은 엔티티의 의도 동사 메서드가 소유한다.
- setter로 상태를 변경하지 않는다.
- 전이 불가 시 도메인 예외를 던진다.
- 전이가 많으면 `EnumMap<State, Set<State>>` 상수로 관리한다.
- 단일 애그리거트 전이는 `Modifier`가 엔트리다.
- 여러 애그리거트 조율은 `Processor`가 담당한다.
- 상태 변경 자체는 엔티티 메서드가 강제한다.
- 트랜잭션당 애그리거트 규칙은 → [architecture](architecture.md)의 트랜잭션 경계.


### 물리 FK 금지

- 물리 FK를 만들지 않는다.
- 참조 정합성은 애플리케이션이 책임진다.
- 모든 연관에 `@ForeignKey(ConstraintMode.NO_CONSTRAINT)`를 명시한다.
- 마이그레이션 DDL에도 물리 FK를 만들지 않는다.
- 경계를 넘는 참조는 `UUID xxxId`로 저장한다.
- 객체 연관은 같은 애그리거트 내부에서만 사용한다.
- 모든 `xxx_id` 컬럼 인덱스는 Flyway가 생성한다.
- `ddl-auto=validate`는 인덱스를 검증하지 않는다.


### 연관

- 단방향 다대일을 기본으로 한다.
- 모든 연관에 `fetch = FetchType.LAZY`를 명시한다.
- 경계를 넘는 양방향 연관을 사용하지 않는다.
- 역방향 조회는 리포지토리 쿼리로 해결한다.
- 애그리거트 내부는 자식 → 부모 `@ManyToOne` 단방향이 기본이다.
- 부모 → 자식 생명주기가 필요하면 `mappedBy` 양방향을 사용한다.
- 단방향 `@OneToMany + @JoinColumn`은 자식 INSERT 후 FK UPDATE가 추가로 발생하므로 사용하지 않는다.
- `mappedBy` 전용 FK 필드는 자바 코드에서 직접 사용하지 않을 수 있다.
- 정적 분석이 미사용으로 판단하면 `@Keep` 같은 마커로 프레임워크 사용 의도를 표시한다.
- `@SuppressWarnings`로 검사를 끄지 않는다.
- `@OneToMany` 컬렉션은 `Set`으로 선언한다.
- `List` 컬렉션은 일부 삭제 시 delete-all-reinsert가 발생할 수 있으므로 기본 컬렉션으로 사용하지 않는다.
- 두 개 이상의 `List` 연관을 동시에 fetch join하면 `MultipleBagFetchException`이 발생할 수 있다.
- 순서가 의미 있으면 `@OrderColumn`을 명시한다.
- `@ManyToMany`를 금지한다.
- 연결 테이블은 독립 엔티티로 승격한다.
- 같은 애그리거트 연결은 양쪽 `@ManyToOne`으로 매핑한다.
- 서로 다른 애그리거트 연결은 양쪽 ID만 보관한다.
- 신규 자식은 부모 컬렉션을 통해 생성·정리한다.
- `CascadeType.REMOVE`만으로는 삭제만 전파되고 생성은 전파되지 않는다.


### 버저닝 (`@Version`)

- 기본 전략은 last-write-wins다.
- 경합 민감 상태전이만 `@Version`을 사용한다.
- `version BIGINT default 0` 컬럼을 Flyway로 추가한다.
- `ddl-auto=validate` 환경에서는 `version` 컬럼이 없으면 스키마 검증 오류로 애플리케이션 기동이 실패한다.
- 낙관락 충돌은 409로 응답한다.
- 서버는 자동 재시도하지 않는다.
- 클라이언트가 재시도한다.
- 재시도 폭주가 확인되면 비관락으로 승격한다.


### 소프트삭제

- 삭제는 논리삭제를 기본으로 한다.
- nullable `deletedAt` 컬럼을 둔다.
- 물리 DELETE 대신 `delete()` 메서드로 `deletedAt`을 설정한다.
- `Remover`가 `delete()`를 호출한다.
- 부모 소프트삭제 시 자식도 같은 트랜잭션에서 소프트삭제한다.


### 생성 진입점

- 생성 진입점·생성자 규칙은 → [coding-conventions](coding-conventions.md)의 객체 생성.

```java
@Entity
@Table(
    schema = "users",
    name = "users",
    indexes = @Index(name = "idx_user_team_id", columnList = "team_id")
)
public class User extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Convert(converter = EmailConverter.class)
    private Email email;

    @Column
    private String name;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "team_id")
    @Nullable
    private UUID teamId;

    @Nullable
    private Instant deletedAt;

    protected User() {}

    private User(UUID id, Email email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.status = Status.PENDING;
    }

    public static User create(String email, String name) {
        return new User(UuidV7Generator.generate(), Email.of(email), name);
    }

    public void activate() {
        if (status != Status.PENDING) {
            throw new UserStatusException(UserErrorCode.NOT_ACTIVATABLE);
        }
        this.status = Status.ACTIVE;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
```
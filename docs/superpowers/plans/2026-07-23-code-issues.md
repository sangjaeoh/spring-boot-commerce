# 코드 이슈 4건·인증 표면표 압축 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 감사가 발견한 코드 결함 4건을 TDD로 수정하고 인증 표면표를 압축한다.

**Architecture:** 항목별 독립 태스크 5개. 코드 변경(1~4)은 결함 재현 실패 테스트 → 수정 → 통과 → 관련 문서 갱신 → 커밋. 항목 5는 문서만.

**Tech Stack:** Java 25 · Spring Boot 4.1 · Spring Data JPA · Flyway · JUnit 6 · Testcontainers · MockMvc

**Spec:** `docs/superpowers/specs/2026-07-23-code-issues-design.md`

## Global Constraints

- 작업 브랜치: main에서 `fix/code-issues` 생성 후 시작(Task 1 Step 0).
- TDD — 각 코드 항목은 결함을 재현하는 실패 테스트를 먼저 쓰고 통과시킨다.
- 행동 변경은 같은 태스크에서 REQUIREMENTS.md·DOMAIN_MODEL.md 해당 서술 갱신을 동반한다(문서 무모순 유지). 문서는 현재 상태로 서술, 편집 이력 서술 금지.
- 마이그레이션은 Flyway 신규 버전 파일로만 추가한다(기존 파일 불변).
- 항목당 커밋 1개. 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` 트레일러.
- 각 태스크 종료 전 해당 모듈 테스트 + `./gradlew spotlessApply` 후 위반 확인. 최종 태스크 후 전체 `./gradlew build`.
- ArchUnit 규칙: 컨트롤러가 여러 도메인을 조율하면 파사드로 옮긴다. `@Entity`는 소유 도메인 모듈의 `domain` 패키지에만. provided/Info 시그니처에 JPA 매핑 타입 노출 금지. apps는 도메인 리포지토리 직접 접근 금지.
- 시그니처 확인 지시가 있는 단계는 그 grep을 먼저 실행하고 실제 시그니처에 맞춘다 — 계획의 코드는 그 외 부분에서 그대로 쓴다.

---

### Task 1: inquiry 작성 자격 게이트·상품 검증

**Files:**
- Create: `module-apps/app-api/src/main/java/com/commerce/app/api/facade/InquiryWriteFacade.java`
- Modify: `module-apps/app-api/src/main/java/com/commerce/app/api/web/v1/inquiry/InquiryController.java` (작성 경로만 파사드 경유로)
- Modify: `module-apps/app-api/src/main/java/com/commerce/app/api/exception/ApiErrorCode.java` (`INQUIRY_NOT_ELIGIBLE` 추가)
- Test: `module-apps/app-api/src/test/java/com/commerce/app/api/web/v1/inquiry/InquiryControllerTest.java`
- Docs: `REQUIREMENTS.md`(246행 문의 절), `DOMAIN_MODEL.md`(§10 문의 정책·불변식)

**Interfaces:**
- Consumes: `MemberReader.getMember(UUID): MemberInfo`(탈퇴는 `MemberNotFoundException`), `MemberInfo.status(): MemberStatus`, `ProductReader.getProduct(UUID): ProductInfo`(삭제는 `ProductNotFoundException`, 숨김 통과), `InquiryAppender.write(UUID memberId, UUID productId, String content, boolean secret): UUID`
- Produces: `InquiryWriteFacade.write(UUID memberId, UUID productId, String content, boolean secret): UUID`

- [ ] **Step 0: 브랜치 생성**

```bash
git checkout -b fix/code-issues main
```

- [ ] **Step 1: 실패 테스트 작성**

`InquiryControllerTest`에 추가. 정지 회원 만들기는 기존 테스트의 정지 헬퍼를 따른다 — 먼저 `grep -rn "suspend" module-apps/app-api/src/test --include="*.java" | head -5`로 실제 호출(빈·시그니처)을 확인하고 맞춘다.

```java
@Test
@DisplayName("정지 회원의 문의 작성은 자격 미달로 거부된다")
void writeRejectsSuspendedMember() throws Exception {
    UUID memberId = registerMember();
    UUID productId = seedOnSaleProduct("문의 상품", 10_000L, 5).productId();
    // 정지 — 실제 정지 헬퍼/빈 시그니처에 맞춘다(위 grep)
    memberModifier.suspend(memberId, SuspensionReason.ABUSE);

    writeInquiry(memberId, productId, new InquiryRequest("문의 본문", false))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("API_INQUIRY_NOT_ELIGIBLE"));
}

@Test
@DisplayName("존재하지 않는 상품에 대한 문의 작성은 부재로 거부된다")
void writeRejectsUnknownProduct() throws Exception {
    UUID memberId = registerMember();

    writeInquiry(memberId, UUID.randomUUID(), new InquiryRequest("문의 본문", false))
            .andExpect(status().isNotFound());
}
```

`seedOnSaleProduct(...)`는 부모 `WebIntegrationTest`의 헬퍼 — 반환 타입·인자는 `module-apps/app-api/src/test/java/com/commerce/app/api/web/v1/WebIntegrationTest.java:84-98`에서 확인해 맞춘다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :module-apps:app-api:test --tests "com.commerce.app.api.web.v1.inquiry.InquiryControllerTest" 2>&1 | tail -20`
Expected: 신규 2건 FAIL(현재 201 반환 — 검증 부재), 기존 통과.

- [ ] **Step 3: 구현**

`ApiErrorCode`에 기존 값들과 같은 형식으로 추가(배치는 REVIEW_NOT_ELIGIBLE 옆):

```java
INQUIRY_NOT_ELIGIBLE("API_INQUIRY_NOT_ELIGIBLE", "문의를 작성할 자격이 없는 회원이다.", 409),
```

`InquiryWriteFacade` 신설(ReviewWriteFacade·CartCommandFacade 패턴):

```java
package com.commerce.app.api.facade;

// import는 파일 내 다른 파사드와 동일 스타일로

/** 문의 작성을 조율한다 — 자격 활성 회원과 실존 상품만 허용한다. */
@Component
public class InquiryWriteFacade {

    private final MemberReader memberReader;
    private final ProductReader productReader;
    private final InquiryAppender inquiryAppender;

    public InquiryWriteFacade(
            MemberReader memberReader, ProductReader productReader, InquiryAppender inquiryAppender) {
        this.memberReader = memberReader;
        this.productReader = productReader;
        this.inquiryAppender = inquiryAppender;
    }

    public UUID write(UUID memberId, UUID productId, String content, boolean secret) {
        // 1. 자격 확인 — 탈퇴 회원은 getMember가 부재로 거부한다
        if (memberReader.getMember(memberId).status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.INQUIRY_NOT_ELIGIBLE);
        }
        // 2. 상품 존재·미삭제 확인 — 숨김 상품은 통과한다(구매 후 숨김된 상품에도 문의 가능)
        productReader.getProduct(productId);
        // 3. 작성
        return inquiryAppender.write(memberId, productId, content, secret);
    }
}
```

`InquiryController`: 필드·생성자에서 `InquiryAppender`를 `InquiryWriteFacade`로 교체(조회용 `InquiryReader`는 유지), `write` 메서드 본문을 `inquiryWriteFacade.write(...)` 호출로 변경.

- [ ] **Step 4: 기존 테스트 정합화**

`InquiryControllerTest`의 기존 테스트들은 상품 시딩 없이 `UUID.randomUUID()`를 productId로 쓴다 — 이제 404가 나므로, 작성이 필요한 각 테스트에서 `seedOnSaleProduct(...)`로 실제 상품을 시딩해 productId를 얻도록 수정한다.

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :module-apps:app-api:test --tests "com.commerce.app.api.web.v1.inquiry.InquiryControllerTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 문서 갱신**

- `REQUIREMENTS.md:246` "구매 이력이나 회원 자격 상태와 무관하게 인증된 회원이면 작성할 수 있다." → 자격 활성(활성 ∧ 미탈퇴) 회원이 실존(미삭제) 상품에만 작성할 수 있고, 숨김 상품은 허용(구매 후 숨김 대응)이라는 서술로 교체. 구매 이력 무관은 유지.
- `DOMAIN_MODEL.md` §10 문의 정책·불변식: 작성 검증(자격·상품 실존)은 앱 계층 파사드가 조율한다는 서술 추가(도메인은 본문 검증만 소유 — 현행 유지).

- [ ] **Step 7: 커밋**

```bash
./gradlew spotlessApply -q
git add -A
git commit -m "fix: 문의 작성에 자격 활성·상품 실존 검증 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: review 본인 삭제 소프트삭제 통일

**Files:**
- Create: `module-domains/domain-review/src/main/java/com/commerce/domain/review/domain/DeletedBy.java`
- Create: `module-domains/domain-review/src/main/resources/db/migration/review/V3__unify_soft_delete_and_allow_rewrite.sql`
- Modify: `module-domains/domain-review/src/main/java/com/commerce/domain/review/domain/Review.java`
- Modify: `module-domains/domain-review/src/main/java/com/commerce/domain/review/application/DefaultReviewRemover.java`
- Modify: `module-domains/domain-review/src/main/java/com/commerce/domain/review/application/required/ReviewRepository.java`
- Modify: `module-domains/domain-review/src/main/java/com/commerce/domain/review/application/DefaultReviewAppender.java` (중복 검사 호출·주석)
- Test: `module-domains/domain-review/src/test/java/com/commerce/domain/review/application/ReviewPersistenceTest.java`
- Docs: `REQUIREMENTS.md`(리뷰 절 235-238행 부근), `DOMAIN_MODEL.md`(§9 리뷰 필드·정책)

**Interfaces:**
- Consumes: `Review.delete(String reason)`(관리자 소프트삭제, 기존), `ReviewRepository.findByIdAndMemberIdAndDeletedAtIsNull(...)`(기존)
- Produces: `DeletedBy` enum(`MEMBER`, `ADMIN`), `Review.deleteByOwner()`, `ReviewRepository.existsActiveOrAdminRemoved(UUID memberId, UUID productId): boolean`

- [ ] **Step 1: 실패 테스트 작성**

`ReviewPersistenceTest`에 추가(기존 스타일 — `reviewAppender.write`로 픽스처 생성):

```java
@Test
@DisplayName("본인 삭제는 소프트삭제로 행을 남기고, 삭제 후 같은 상품에 재작성할 수 있다")
void ownRemoveSoftDeletesAndAllowsRewrite() {
    UUID memberId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID reviewId = reviewAppender.write(memberId, productId, 4, "첫 리뷰");

    reviewRemover.remove(reviewId, memberId);

    Review removed = reviewRepository.findById(reviewId).orElseThrow();
    assertThat(removed.getDeletedAt()).isNotNull();
    assertThat(removed.getDeletedBy()).isEqualTo(DeletedBy.MEMBER);

    UUID rewritten = reviewAppender.write(memberId, productId, 5, "다시 쓴 리뷰");
    assertThat(rewritten).isNotEqualTo(reviewId);
}

@Test
@DisplayName("관리자 삭제 후 재작성은 계속 거부된다")
void adminRemoveStillBlocksRewrite() {
    UUID memberId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID reviewId = reviewAppender.write(memberId, productId, 4, "첫 리뷰");

    reviewRemover.removeByAdmin(reviewId, "부적절한 내용");

    assertThatThrownBy(() -> reviewAppender.write(memberId, productId, 5, "다시 쓴 리뷰"))
            .isInstanceOf(DuplicateReviewException.class);
}
```

`Review`의 getter 이름(`getDeletedAt` 등)은 파일에서 확인해 맞춘다. 첫 테스트는 `getDeletedBy`가 없어 컴파일 실패 — 그것이 실패 확인이다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :module-domains:domain-review:test --tests "com.commerce.domain.review.application.ReviewPersistenceTest" 2>&1 | tail -10`
Expected: 컴파일 실패(`DeletedBy`·`getDeletedBy` 미존재).

- [ ] **Step 3: 마이그레이션 V3**

`V3__unify_soft_delete_and_allow_rewrite.sql`:

```sql
ALTER TABLE review.review
    ADD COLUMN deleted_by VARCHAR(20);

UPDATE review.review SET deleted_by = 'ADMIN' WHERE deleted_at IS NOT NULL;

DROP INDEX review.ux_review_member_product;

CREATE UNIQUE INDEX ux_review_member_product_active
    ON review.review (member_id, product_id)
    WHERE deleted_at IS NULL;
```

(부분 유니크 선례: `ux_member_email_active`. 관리자 삭제 행의 재작성 차단은 애플리케이션 검사(REQUIRES_NEW)가 소유한다 — DB 백업이 없는 것은 기존 `existsIncludingDeleted`도 동일 수준이라 수용.)

- [ ] **Step 4: 도메인 구현**

`DeletedBy.java` 신설:

```java
package com.commerce.domain.review.domain;

/** 리뷰 삭제 주체. 본인 삭제(MEMBER)는 재작성을 막지 않고, 관리자 삭제(ADMIN)는 막는다. */
public enum DeletedBy {
    MEMBER,
    ADMIN
}
```

`Review.java`:
- 필드 추가(removedReason 아래, 기존 스타일):

```java
/** 삭제 주체. 삭제된 리뷰만 있다. */
@Nullable @Enumerated(EnumType.STRING)
@Column(name = "deleted_by")
private DeletedBy deletedBy;
```

- 기존 `delete(String reason)`에 `this.deletedBy = DeletedBy.ADMIN;` 한 줄 추가.
- 본인 삭제 메서드 추가:

```java
/** 본인 삭제로 소프트삭제한다. 사유 없이 주체만 남긴다. */
public void deleteByOwner() {
    this.deletedAt = Instant.now();
    this.deletedBy = DeletedBy.MEMBER;
}
```

- getter `getDeletedBy()` 추가(기존 getter 스타일).

`DefaultReviewRemover.remove`: `reviewRepository.delete(...)` 물리 삭제를 소프트삭제로 교체:

```java
@Transactional
@Override
public void remove(UUID reviewId, UUID memberId) {
    reviewRepository
            .findByIdAndMemberIdAndDeletedAtIsNull(reviewId, memberId)
            .orElseThrow(() -> new ReviewNotFoundException(ReviewErrorCode.REVIEW_NOT_FOUND))
            .deleteByOwner();
}
```

`ReviewRepository`: `existsByMemberIdAndProductIdIncludingDeleted`를 교체(주석도 갱신 — 관리자 삭제만 재작성을 막는다):

```java
/** 활성 리뷰 또는 관리자 삭제 리뷰가 있는지 본다 — 본인 삭제(MEMBER)는 재작성을 막지 않는다. */
@Query("select count(r) > 0 from Review r"
        + " where r.memberId = :memberId and r.productId = :productId"
        + " and (r.deletedAt is null or r.deletedBy = com.commerce.domain.review.domain.DeletedBy.ADMIN)")
boolean existsActiveOrAdminRemoved(UUID memberId, UUID productId);
```

`DefaultReviewAppender.writeOnce`: 호출부를 `existsActiveOrAdminRemoved`로 교체, 인접 주석("재작성 불가 정책 — ...")을 새 정책으로 갱신. 유니크 충돌 → `DuplicateReviewException` 변환 로직은 유지(활성 행 동시 중복은 부분 유니크가 계속 막는다).

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :module-domains:domain-review:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (기존 `writeRejectsDuplicate`·`duplicateRowViolatesUniqueIndex`·`removeAppliesToOwnReviewOnly` 포함 전부 통과 — 기존 테스트가 물리 삭제를 단언하면 소프트삭제 단언으로 수정)

Run: `./gradlew :module-apps:app-api:test --tests "com.commerce.app.api.web.v1.review.ReviewControllerTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (`removeDeletesOwnReview`는 목록 제외 단언이라 그대로 통과)

- [ ] **Step 6: 문서 갱신**

- `DOMAIN_MODEL.md` §9 리뷰: 필드 표에 `deletedBy` 추가, 본인 삭제=소프트삭제(사유 없음·주체 MEMBER), 재작성 정책(활성·관리자 삭제 행이 막고 본인 삭제 행은 안 막음), 부분 유니크 인덱스 서술 갱신.
- `REQUIREMENTS.md` 리뷰 절: 본인 삭제 후 재작성 가능(현행 유지), 관리자 삭제 후 재작성 불가 — 서술이 이미 맞으면 삭제 방식 언급만 정리(기획 문서라 소프트/물리 상세는 DOMAIN_MODEL 참조).

- [ ] **Step 7: 커밋**

```bash
./gradlew spotlessApply -q
git add -A
git commit -m "fix: 리뷰 본인 삭제를 소프트삭제로 통일 — 재작성 허용 유지

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 재입고 알림 회원 단위 발송 추적

**Files:**
- Create: `module-domains/domain-wishlist/src/main/resources/db/migration/wishlist/V3__restock_notification_per_member.sql`
- Modify: `module-domains/domain-wishlist/src/main/java/com/commerce/domain/wishlist/domain/RestockNotification.java`
- Modify: `module-domains/domain-wishlist/src/main/java/com/commerce/domain/wishlist/application/required/RestockNotificationRepository.java`
- Modify: `module-domains/domain-wishlist/src/main/java/com/commerce/domain/wishlist/application/StockRestockedListener.java`
- Test: `module-domains/domain-wishlist/src/test/java/com/commerce/domain/wishlist/application/StockRestockedListenerTest.java`
- Docs: `DOMAIN_MODEL.md`(§8 위시리스트 — RestockNotification 필드 표 796-817행·발송 서술 830-833행), `REQUIREMENTS.md`(232행 보강)

**Interfaces:**
- Consumes: `MailGateway.sendRestockMail(String to, String productName)`, `MemberReader.getMembers(Collection<UUID>): List<MemberInfo>`, `OutboxRelay`의 이벤트 단위 재시도(무변경)
- Produces: `RestockNotification.create(UUID eventId, UUID memberId)`, `RestockNotificationRepository.existsByEventIdAndMemberId(UUID, UUID): boolean`

- [ ] **Step 1: 실패 테스트 작성**

`StockRestockedListenerTest`에 추가(기존 스타일 — 찜은 실 `wishlistAppender`, 타 도메인·메일은 `@MockitoBean`). `MemberInfo`의 ID 접근자는 `grep -n "record MemberInfo" -A3 module-domains/domain-member/src/main/java/com/commerce/domain/member/application/info/MemberInfo.java`로 확인해 맞춘다.

```java
@Test
@DisplayName("일부 회원 발송 실패 후 재전달 시 성공분은 건너뛰고 실패분에게만 발송한다")
void partialFailureResendsOnlyUnsent() {
    UUID productId = UUID.randomUUID();
    UUID variantId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    wishlistAppender.add(first, productId);
    wishlistAppender.add(second, productId);
    stubProduct(variantId, productId, "잘 팔리는 티셔츠");
    when(memberReader.getMembers(any()))
            .thenReturn(List.of(
                    memberInfo(first, "first@example.com"), memberInfo(second, "second@example.com")));
    // 첫 시도에서 두 번째 회원 발송만 실패시킨다
    doThrow(new RuntimeException("smtp down"))
            .doNothing()
            .when(mailGateway)
            .sendRestockMail(eq("second@example.com"), any());
    StockRestocked event = new StockRestocked(UUID.randomUUID(), variantId, Instant.now());

    assertThatThrownBy(() -> listener.on(event)).isInstanceOf(RuntimeException.class);
    listener.on(event); // 재전달(아웃박스 재시도에 해당)

    verify(mailGateway, times(1)).sendRestockMail("first@example.com", "잘 팔리는 티셔츠");
    verify(mailGateway, times(2)).sendRestockMail("second@example.com", "잘 팔리는 티셔츠");
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :module-domains:domain-wishlist:test --tests "com.commerce.domain.wishlist.application.StockRestockedListenerTest" 2>&1 | tail -10`
Expected: 신규 테스트 FAIL — 현재 구조는 재전달 시 first에게도 재발송(`times(1)` 위반, 실제 2회).

- [ ] **Step 3: 마이그레이션 V3**

`V3__restock_notification_per_member.sql`:

```sql
ALTER TABLE wishlist.restock_notification
    ADD COLUMN member_id UUID;

DROP INDEX wishlist.ux_restock_notification_event;

CREATE UNIQUE INDEX ux_restock_notification_event_member
    ON wishlist.restock_notification (event_id, member_id);
```

(기존 행은 member_id NULL의 이벤트 단위 기록으로 남는다 — 이미 발행 완료된 이벤트의 흔적이라 신규 로직과 충돌하지 않는다.)

- [ ] **Step 4: 구현**

`RestockNotification`: `memberId` 필드(`@Column(name = "member_id")`) 추가, 정적 팩토리를 `create(UUID eventId, UUID memberId)`로 변경(기존 단일 인자 팩토리는 사용처가 리스너뿐이므로 교체).

`RestockNotificationRepository`:

```java
public interface RestockNotificationRepository extends JpaRepository<RestockNotification, UUID> {
    boolean existsByEventIdAndMemberId(UUID eventId, UUID memberId);
}
```

(`existsByEventId`는 이 변경으로 미사용 — 제거한다.)

`StockRestockedListener.on` 재작성(클래스 주석의 "이벤트 단위 기록" 서술도 회원 단위로 갱신):

```java
@EventListener
public void on(StockRestocked event) {
    // 수신자 해석
    UUID productId = productVariantReader.getVariant(event.variantId()).productId();
    String productName = productReader.getProduct(productId).name();
    List<UUID> wisherIds = wishlistItemRepository.findAllByProductId(productId).stream()
            .map(WishlistItem::getMemberId)
            .toList();
    if (wisherIds.isEmpty()) {
        return;
    }
    // 회원 단위 발송·기록 — 발송은 취소 불가 부작용이라 트랜잭션 밖, 성공분만 기록해
    // 재전달 시 건너뛴다. 발송과 기록 사이 크래시 창은 그 회원 1명 재발송(at-least-once)이다.
    for (MemberInfo member : memberReader.getMembers(wisherIds)) {
        if (restockNotificationRepository.existsByEventIdAndMemberId(event.eventId(), member.id())) {
            continue;
        }
        mailGateway.sendRestockMail(member.email(), productName);
        restockNotificationRepository.save(RestockNotification.create(event.eventId(), member.id()));
    }
}
```

(`member.id()` 접근자명은 Step 1의 grep 결과에 맞춘다. 실패 시 예외는 그대로 전파 — OutboxRelay가 이벤트 단위 재시도, 성공분은 행이 걸러낸다.)

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :module-domains:domain-wishlist:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (기존 `consumeSendsRestockMailToWishers`·`duplicateDeliveryIsIdempotent` 포함 — 후자는 전 회원 기록 존재로 여전히 1회 발송)

- [ ] **Step 6: 문서 갱신**

- `DOMAIN_MODEL.md` §8: RestockNotification 필드 표에 `memberId` 추가, 830행의 "이벤트 단위 기록 — 전원 재발송" 서술을 회원 단위 기록·성공분 스킵·크래시 창은 1명 재발송으로 교체, 유니크 인덱스 서술 갱신.
- `REQUIREMENTS.md:232` 보강: 같은 이벤트 재전달·부분 실패 재시도에도 이미 받은 회원에게 중복 발송하지 않는다.

- [ ] **Step 7: 커밋**

```bash
./gradlew spotlessApply -q
git add -A
git commit -m "fix: 재입고 알림 발송을 회원 단위로 추적 — 부분 실패 시 중복 발송 제거

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 부분환불 PG 취소 거래 영속 (PaymentRefund)

**Files:**
- Create: `module-domains/domain-payment/src/main/java/com/commerce/domain/payment/domain/PaymentRefund.java`
- Create: `module-domains/domain-payment/src/main/java/com/commerce/domain/payment/application/required/PaymentRefundRepository.java`
- Create: `module-domains/domain-payment/src/main/resources/db/migration/payment/V3__create_payment_refund_table.sql`
- Modify: `module-domains/domain-payment/src/main/java/com/commerce/domain/payment/application/DefaultPaymentProcessor.java:83-108`
- Test: `module-domains/domain-payment/src/test/java/com/commerce/domain/payment/application/PaymentPersistenceTest.java`
- Docs: `REQUIREMENTS.md`(결제 절), `DOMAIN_MODEL.md`(§7 결제)

**Interfaces:**
- Consumes: `PaymentGateway.cancelPartially(String pgTransactionId, Money amount, String idempotencyKey): String`(PG 취소 거래 ID 반환), `Payment.syncPartialRefund(Money, Instant)`, `MoneyConverter`, `UuidV7Generator.generate()`, `BaseTimeEntity<UUID>`
- Produces: `PaymentRefund.record(UUID paymentId, Money amount, @Nullable String pgCancelTransactionId, UUID refundKey): PaymentRefund`, `PaymentRefundRepository.existsByRefundKey(UUID): boolean`, `PaymentRefundRepository.findAllByPaymentId(UUID): List<PaymentRefund>`

- [ ] **Step 1: 실패 테스트 작성**

`PaymentPersistenceTest`에 추가(기존 스타일 — `paymentAppender.request(...)`로 픽스처, `StubPaymentGateway` 주입 구조는 파일 상단에서 확인). StubPaymentGateway의 `cancelPartially` 반환값 형식을 먼저 확인한다: `grep -n "cancelPartially" -A3 module-domains/domain-payment/src/test/java/com/commerce/domain/payment/application/StubPaymentGateway.java`

```java
@Test
@DisplayName("부분환불은 PG 취소 거래 ID를 실은 환불 행을 남기고, 같은 라인 재시도는 행을 중복 생성하지 않는다")
void cancelPartiallyPersistsRefundRowIdempotently() {
    UUID paymentId = paymentAppender.request(UUID.randomUUID(), Money.of(30_000L), PaymentMethod.CARD).id();
    paymentProcessor.approve(paymentId); // 승인 경로는 파일 내 기존 승인 헬퍼/스텁 흐름에 맞춘다
    UUID lineId = UUID.randomUUID();

    paymentProcessor.cancelPartially(paymentId, Money.of(10_000L), Money.of(10_000L), lineId);
    paymentProcessor.cancelPartially(paymentId, Money.of(10_000L), Money.of(10_000L), lineId); // 재시도

    List<PaymentRefund> refunds = paymentRefundRepository.findAllByPaymentId(paymentId);
    assertThat(refunds).hasSize(1);
    assertThat(refunds.get(0).getRefundKey()).isEqualTo(lineId);
    assertThat(refunds.get(0).getAmount()).isEqualTo(Money.of(10_000L));
    assertThat(refunds.get(0).getPgCancelTransactionId()).isNotNull();
}
```

승인까지 끌고 가는 정확한 경로(approve 시그니처·스텁 승인 응답)는 `PaymentPersistenceTest` 기존 테스트의 승인 픽스처를 그대로 따른다 — 파일을 읽고 같은 방식으로 쓴다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :module-domains:domain-payment:test --tests "com.commerce.domain.payment.application.PaymentPersistenceTest" 2>&1 | tail -10`
Expected: 컴파일 실패(`PaymentRefund`·`PaymentRefundRepository` 미존재).

- [ ] **Step 3: 마이그레이션 V3**

`V3__create_payment_refund_table.sql`:

```sql
CREATE TABLE payment.payment_refund (
    id                       UUID        NOT NULL,
    payment_id               UUID        NOT NULL,
    amount                   BIGINT      NOT NULL,
    pg_cancel_transaction_id VARCHAR(255),
    refund_key               UUID        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_payment_refund PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_payment_refund_refund_key ON payment.payment_refund (refund_key);

CREATE INDEX ix_payment_refund_payment_id ON payment.payment_refund (payment_id);
```

- [ ] **Step 4: 엔티티·리포지토리 구현**

`PaymentRefund.java`(Payment·RestockNotification 매핑 패턴):

```java
package com.commerce.domain.payment.domain;

// import는 Payment.java와 동일 스타일

/**
 * 부분환불 거래 기록. 부분환불 1건당 1행 — PG 취소 거래 ID로 건별 추적하고,
 * refund_key(주문 라인 ID) 유니크로 재시도 중복 기록을 막는다.
 */
@Entity
@Table(schema = "payment", name = "payment_refund")
public class PaymentRefund extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount")
    private Money amount;

    /** PG 취소 거래 ID. 무 PG 승인·0원 환불은 PG 호출이 없어 비어 있다. */
    @Nullable @Column(name = "pg_cancel_transaction_id")
    private String pgCancelTransactionId;

    /** 라인 멱등 키(주문 라인 ID). 한 라인당 부분환불 1건을 식별한다. */
    @Column(name = "refund_key")
    private UUID refundKey;

    protected PaymentRefund() {}

    private PaymentRefund(UUID id, UUID paymentId, Money amount, @Nullable String pgCancelTransactionId, UUID refundKey) {
        this.id = id;
        this.paymentId = paymentId;
        this.amount = amount;
        this.pgCancelTransactionId = pgCancelTransactionId;
        this.refundKey = refundKey;
    }

    public static PaymentRefund record(
            UUID paymentId, Money amount, @Nullable String pgCancelTransactionId, UUID refundKey) {
        return new PaymentRefund(UuidV7Generator.generate(), paymentId, amount, pgCancelTransactionId, refundKey);
    }

    // getter들은 Payment.java의 getter 스타일로: getPaymentId, getAmount, getPgCancelTransactionId, getRefundKey
}
```

(`@Id` 선언·`getId()` 오버라이드 여부는 `RestockNotification.java`의 실제 형태를 그대로 따른다.)

`PaymentRefundRepository.java`:

```java
package com.commerce.domain.payment.application.required;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    boolean existsByRefundKey(UUID refundKey);

    List<PaymentRefund> findAllByPaymentId(UUID paymentId);
}
```

- [ ] **Step 5: 프로세서 수정**

`DefaultPaymentProcessor`: 생성자에 `PaymentRefundRepository paymentRefundRepository` 주입 추가. `cancelPartially`·`recordPartialRefundSync` 교체:

```java
@Override
public void cancelPartially(UUID paymentId, Money amount, Money cumulativeTotal, UUID refundKey) {
    Payment payment = find(paymentId);
    if (payment.getStatus() == PaymentStatus.CANCELLED) {
        return;
    }
    if (payment.getStatus() != PaymentStatus.APPROVED) {
        throw new PaymentStatusException(PaymentErrorCode.INVALID_PAYMENT_STATE_TRANSITION);
    }
    String pgTransactionId = payment.getPgTransactionId();
    if (pgTransactionId == null || amount.isZero()) {
        // 무 PG 승인(전액 할인)과 0원 환불은 PG 호출 없이 기록만 동기화한다.
        inTransaction(() -> recordPartialRefundSync(paymentId, amount, cumulativeTotal, refundKey, null));
        return;
    }
    // 환불은 비가역이라 역보상할 수 없다 — 라인 단위 멱등 키로 재시도 이중 환불을, 누계 동기화로 이중 기록을 막는다.
    String pgCancelTransactionId =
            paymentGateway.cancelPartially(pgTransactionId, amount, "partial-refund:" + refundKey);
    inTransaction(() -> recordPartialRefundSync(paymentId, amount, cumulativeTotal, refundKey, pgCancelTransactionId));
}

/** 부분 환불 누계를 단조 동기화하고 환불 행을 남긴다(refund_key 재시도는 행을 중복 생성하지 않는다). */
private PaymentInfo recordPartialRefundSync(
        UUID paymentId,
        Money amount,
        Money cumulativeTotal,
        UUID refundKey,
        @Nullable String pgCancelTransactionId) {
    Payment payment = find(paymentId);
    payment.syncPartialRefund(cumulativeTotal, clock.instant());
    if (!paymentRefundRepository.existsByRefundKey(refundKey)) {
        paymentRefundRepository.save(PaymentRefund.record(paymentId, amount, pgCancelTransactionId, refundKey));
    }
    return PaymentInfo.from(payment);
}
```

- [ ] **Step 6: 통과 확인**

Run: `./gradlew :module-domains:domain-payment:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :module-apps:app-api:test --tests "com.commerce.app.api.facade.OrderCancellationFacadeTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (기존 부분취소 재개·재시도 테스트 무손상)

- [ ] **Step 7: 문서 갱신**

- `DOMAIN_MODEL.md` §7 결제: PaymentRefund 필드 표(기존 엔티티 표 형식) 추가, 부분환불 오퍼레이션 서술에 환불 행 기록·refund_key 멱등 추가.
- `REQUIREMENTS.md` 결제 절: "승인 거래와 취소(환불) 거래를 각각 식별·추적" 서술에 부분환불 건별 기록이 포함됨을 반영.

- [ ] **Step 8: 커밋**

```bash
./gradlew spotlessApply -q
git add -A
git commit -m "feat: 부분환불 거래 기록(PaymentRefund) — PG 취소 거래 ID 건별 영속

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 인증 표면표 압축

**Files:**
- Modify: `REQUIREMENTS.md` 인증 절의 엔드포인트 표면 분류(4개 상세 표, 60여 행)

**Interfaces:**
- Consumes: 없음 (문서만)
- Produces: 압축된 표면 분류 표 1개

- [ ] **Step 1: 압축 재작성**

인증 절의 4개 상세 표를 하나의 분류 규칙 표로 교체한다. 형식(각 분류의 강제 방식은 기존 표의 강제 열 서술을 그대로 보존):

```markdown
| 분류 | 판별 | 강제 | 대표 예시 |
|---|---|---|---|
| 공개 | 가입·로그인·카탈로그 조회 등 비로그인 쇼핑 표면 | 무인증 허용 | `POST /api/v1/auth/login`, `GET /api/v1/products` |
| 본인(셀프서비스) | 회원 자원(장바구니·주문·쿠폰·위시리스트·리뷰·문의·주소록 등)의 조회·변경 | 회원을 토큰 주체에서 도출(클라이언트는 memberId를 보내지 않음). 미인증 401 | `POST /api/v1/orders`, `GET /api/v1/wishlists` |
| 관리자 | `/api/v1/admin/**` 전체(app-admin) | 관리자 토큰만 허용. 미인증 401, 관리자가 아닌 주체 403 | `POST /api/v1/admin/orders/{id}/ship` |
| 시스템(웹훅) | PG 결제 확정 통지 수신(app-batch) | PG 공유 시크릿의 HMAC-SHA256 본문 서명(`X-Webhook-Signature`) 상수 시간 비교. 불일치·부재 401 | `POST /api/v1/payments/webhook` |
```

표 아래에 전수 인벤토리 위임 문장 추가: "엔드포인트 전수 목록은 각 앱의 swagger-ui(OpenAPI 표면)가 소유한다 — 이 문서는 분류 규칙만 소유한다."

기존 상세 표에만 있던 정책성 서술(예: 특정 표면의 특이 강제)이 있으면 규칙 표 아래 불릿으로 보존한다 — 단순 엔드포인트 나열은 버린다.

- [ ] **Step 2: 검증**

Run: `grep -cE '^\| (GET|POST|PUT|PATCH|DELETE) \|' REQUIREMENTS.md; true`
Expected: 0 (메서드별 나열 행 제거 — 대표 예시 칸의 인라인 코드는 무관)

인증 절을 통독해 4분류 강제 서술이 전부 보존됐는지 확인.

- [ ] **Step 3: 커밋**

```bash
git add REQUIREMENTS.md
git commit -m "docs: 인증 표면 분류를 규칙·대표 예시로 압축 — 전수 인벤토리는 swagger 위임

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: 전체 게이트·마무리

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 빌드**

Run: `./gradlew build 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (Spotless·NullAway·Error Prone·ArchUnit 게이트 전부 통과)

실패 시 해당 태스크로 돌아가 수정 후 재실행.

- [ ] **Step 2: 문서 정합 확인**

변경된 행동 4건의 REQUIREMENTS·DOMAIN_MODEL 서술이 코드와 일치하는지 항목별로 대조(각 태스크 문서 갱신 스텝의 산출 확인).

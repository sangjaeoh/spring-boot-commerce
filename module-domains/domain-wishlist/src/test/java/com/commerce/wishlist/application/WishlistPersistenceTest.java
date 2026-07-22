package com.commerce.wishlist.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.wishlist.application.info.WishlistItemInfo;
import com.commerce.wishlist.application.provided.WishlistAppender;
import com.commerce.wishlist.application.provided.WishlistReader;
import com.commerce.wishlist.application.provided.WishlistRemover;
import com.commerce.wishlist.application.required.WishlistItemRepository;
import com.commerce.wishlist.domain.WishlistItem;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * wishlist 도메인의 영속 이음새를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>{@code ddl-auto=validate} 정합, 멱등 추가·삭제, (회원, 상품) 유니크, 최신 찜 우선 정렬을 확인한다.
 */
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/wishlist",
            "spring.flyway.schemas=wishlist",
            "spring.flyway.default-schema=wishlist"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({DefaultWishlistAppender.class, DefaultWishlistRemover.class, DefaultWishlistReader.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WishlistPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final WishlistAppender wishlistAppender;
    private final WishlistRemover wishlistRemover;
    private final WishlistReader wishlistReader;
    private final WishlistItemRepository wishlistItemRepository;

    WishlistPersistenceTest(
            WishlistAppender wishlistAppender,
            WishlistRemover wishlistRemover,
            WishlistReader wishlistReader,
            WishlistItemRepository wishlistItemRepository) {
        this.wishlistAppender = wishlistAppender;
        this.wishlistRemover = wishlistRemover;
        this.wishlistReader = wishlistReader;
        this.wishlistItemRepository = wishlistItemRepository;
    }

    @Test
    @DisplayName("찜을 추가하면 목록에 상품 ID와 찜 시각이 보인다")
    void addShowsItemInWishlist() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        wishlistAppender.add(memberId, productId);

        assertThat(wishlistReader.getWishlist(memberId)).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(productId);
            assertThat(item.wishedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("이미 찜한 상품의 재추가는 아무 일도 하지 않는다 — 행 1개 유지")
    void addIsIdempotentForSameProduct() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        wishlistAppender.add(memberId, productId);
        wishlistAppender.add(memberId, productId);

        assertThat(wishlistReader.getWishlist(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("(회원, 상품) 중복 행 직접 저장은 유니크 위반으로 거부된다 — 동시 경합의 최후 방어선")
    void duplicateRowViolatesUniqueIndex() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        wishlistItemRepository.saveAndFlush(WishlistItem.create(memberId, productId));

        assertThatThrownBy(() -> wishlistItemRepository.saveAndFlush(WishlistItem.create(memberId, productId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("찜을 삭제하면 목록에서 빠지고, 없는 찜 삭제는 아무 일도 하지 않는다")
    void removeIsIdempotent() {
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        wishlistAppender.add(memberId, productId);

        wishlistRemover.remove(memberId, productId);
        assertThat(wishlistReader.getWishlist(memberId)).isEmpty();

        wishlistRemover.remove(memberId, productId);
        assertThat(wishlistReader.getWishlist(memberId)).isEmpty();
    }

    @Test
    @DisplayName("목록은 최신 찜 우선으로 정렬된다")
    void wishlistOrdersLatestFirst() {
        UUID memberId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();

        wishlistAppender.add(memberId, firstProductId);
        wishlistAppender.add(memberId, secondProductId);

        assertThat(wishlistReader.getWishlist(memberId))
                .extracting(WishlistItemInfo::productId)
                .containsExactly(secondProductId, firstProductId);
    }
}

package com.commerce.domain.wishlist.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.commerce.domain.member.application.info.MemberInfo;
import com.commerce.domain.member.application.provided.MemberReader;
import com.commerce.domain.member.domain.MemberRole;
import com.commerce.domain.member.domain.MemberStatus;
import com.commerce.domain.product.application.info.ProductInfo;
import com.commerce.domain.product.application.info.ProductVariantInfo;
import com.commerce.domain.product.application.provided.ProductReader;
import com.commerce.domain.product.application.provided.ProductVariantReader;
import com.commerce.domain.product.domain.ProductStatus;
import com.commerce.domain.product.domain.ProductVariantStatus;
import com.commerce.domain.shared.entity.Money;
import com.commerce.domain.wishlist.application.provided.WishlistAppender;
import com.commerce.domain.wishlist.application.required.MailGateway;
import com.commerce.event.stock.StockRestocked;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 재입고 이벤트 소비를 실 wishlist 스키마로 검증하는 테스트다.
 *
 * <p>타 도메인 provided(product·member)와 메일 포트는 모듈 경계 밖 계약이라 목으로 둔다(worklog 5-5).
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
@Import({StockRestockedListener.class, DefaultWishlistAppender.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StockRestockedListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private final StockRestockedListener listener;
    private final WishlistAppender wishlistAppender;

    @MockitoBean
    private ProductVariantReader productVariantReader;

    @MockitoBean
    private ProductReader productReader;

    @MockitoBean
    private MemberReader memberReader;

    @MockitoBean
    private MailGateway mailGateway;

    StockRestockedListenerTest(StockRestockedListener listener, WishlistAppender wishlistAppender) {
        this.listener = listener;
        this.wishlistAppender = wishlistAppender;
    }

    @Test
    @DisplayName("재입고 이벤트 소비는 그 상품을 찜한 활성 회원 각각에게 상품명을 담은 메일을 보낸다")
    void consumeSendsRestockMailToWishers() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID firstWisherId = UUID.randomUUID();
        UUID secondWisherId = UUID.randomUUID();
        wishlistAppender.add(firstWisherId, productId);
        wishlistAppender.add(secondWisherId, productId);
        wishlistAppender.add(UUID.randomUUID(), UUID.randomUUID());
        stubProduct(variantId, productId, "잘 팔리는 티셔츠");
        when(memberReader.getMembers(any()))
                .thenReturn(List.of(
                        memberInfo(firstWisherId, "first@example.com"),
                        memberInfo(secondWisherId, "second@example.com")));

        listener.on(new StockRestocked(UUID.randomUUID(), variantId, Instant.now()));

        verify(mailGateway).sendRestockMail("first@example.com", "잘 팔리는 티셔츠");
        verify(mailGateway).sendRestockMail("second@example.com", "잘 팔리는 티셔츠");
        verifyNoMoreInteractions(mailGateway);
    }

    @Test
    @DisplayName("같은 이벤트의 중복 전달은 발송을 반복하지 않는다")
    void duplicateDeliveryIsIdempotent() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID wisherId = UUID.randomUUID();
        wishlistAppender.add(wisherId, productId);
        stubProduct(variantId, productId, "잘 팔리는 티셔츠");
        when(memberReader.getMembers(any())).thenReturn(List.of(memberInfo(wisherId, "wisher@example.com")));
        StockRestocked event = new StockRestocked(UUID.randomUUID(), variantId, Instant.now());

        listener.on(event);
        listener.on(event);

        verify(mailGateway, times(1)).sendRestockMail("wisher@example.com", "잘 팔리는 티셔츠");
    }

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
                .thenReturn(List.of(memberInfo(first, "first@example.com"), memberInfo(second, "second@example.com")));
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

    /** 변형→상품 해석과 상품명 조회를 스텁한다. */
    private void stubProduct(UUID variantId, UUID productId, String productName) {
        when(productVariantReader.getVariant(variantId))
                .thenReturn(new ProductVariantInfo(
                        variantId,
                        productId,
                        Money.of(10000L),
                        ProductVariantStatus.ACTIVE,
                        "S",
                        null,
                        Instant.now(),
                        Instant.now()));
        when(productReader.getProduct(productId))
                .thenReturn(new ProductInfo(
                        productId, productName, null, ProductStatus.ON_SALE, null, Instant.now(), Instant.now()));
    }

    private static MemberInfo memberInfo(UUID memberId, String email) {
        return new MemberInfo(
                memberId,
                email,
                "테스터",
                MemberRole.BUYER,
                MemberStatus.ACTIVE,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}

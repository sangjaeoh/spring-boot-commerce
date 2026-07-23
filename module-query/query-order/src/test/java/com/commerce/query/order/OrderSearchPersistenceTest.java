package com.commerce.query.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.common.jpa.migration.SchemaFlywayFactory;
import com.commerce.domain.member.domain.Email;
import com.commerce.domain.member.domain.Member;
import com.commerce.domain.member.domain.MemberRole;
import com.commerce.domain.member.domain.WithdrawalReason;
import com.commerce.domain.order.domain.Address;
import com.commerce.domain.order.domain.Order;
import com.commerce.domain.order.domain.OrderLineSnapshot;
import com.commerce.domain.order.domain.OrderStatus;
import com.commerce.domain.shared.entity.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 관리자 주문 검색의 크로스 스키마 조회를 실 PostgreSQL로 검증하는 테스트다.
 *
 * <p>도메인 마이그레이션이 스키마마다 V1이라 단일 Flyway 인스턴스로는 버전이 충돌한다 — 앱 통합 테스트
 * 선례처럼 {@code SchemaFlywayFactory}로 스키마별 독립 마이그레이션을 1회 실행하고 검색 축을 확인한다.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DefaultOrderSearchReader.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrderSearchPersistenceTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        postgres.start();
        SchemaFlywayFactory.migrateAll(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    private final OrderSearchReader orderSearchReader;
    private final TestEntityManager entityManager;

    OrderSearchPersistenceTest(OrderSearchReader orderSearchReader, TestEntityManager entityManager) {
        this.orderSearchReader = orderSearchReader;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("이메일과 상태 복합 조건 검색은 해당 회원의 해당 상태 주문만 최신 주문 우선으로 반환한다")
    void searchFiltersByEmailAndStatus() {
        String email = uniqueEmail();
        UUID memberId = persistMember(email);
        UUID paidOrderId = persistOrder(memberId, true);
        persistOrder(memberId, false);
        persistOrder(persistMember(uniqueEmail()), true);

        var page = orderSearchReader.getMemberOrderPage(email, OrderStatus.PAID, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(info -> {
            assertThat(info.orderId()).isEqualTo(paidOrderId);
            assertThat(info.memberId()).isEqualTo(memberId);
            assertThat(info.memberEmail()).isEqualTo(email);
            assertThat(info.status()).isEqualTo(OrderStatus.PAID);
            assertThat(info.orderNumber()).isNotBlank();
            assertThat(info.orderedAt()).isNotNull();
        });
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 없이 이메일만으로 검색하면 그 회원의 전 상태 주문이 최신 주문 우선으로 반환된다")
    void searchWithoutStatusReturnsAllStatuses() {
        String email = uniqueEmail();
        UUID memberId = persistMember(email);
        UUID paidOrderId = persistOrder(memberId, true);
        awaitNextMillisecond();
        UUID pendingOrderId = persistOrder(memberId, false);

        var page = orderSearchReader.getMemberOrderPage(email, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(OrderSearchInfo::orderId).containsExactly(pendingOrderId, paidOrderId);
    }

    @Test
    @DisplayName("미존재 이메일 검색은 빈 페이지다")
    void searchUnknownEmailReturnsEmptyPage() {
        assertThat(orderSearchReader
                        .getMemberOrderPage(uniqueEmail(), null, PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("탈퇴 회원의 이메일 검색은 빈 페이지다")
    void searchExcludesWithdrawnMember() {
        String email = uniqueEmail();
        Member member = Member.create(Email.of(email), "테스터", "password-hash", MemberRole.BUYER);
        member.delete(WithdrawalReason.NO_LONGER_USED, Instant.now());
        entityManager.persist(member);
        persistOrder(member.getId(), false);

        assertThat(orderSearchReader
                        .getMemberOrderPage(email, null, PageRequest.of(0, 10))
                        .getContent())
                .isEmpty();
    }

    // UUIDv7의 동일 밀리초 내 하위 비트는 랜덤이라, 순서 단언 전에 밀리초 경계를 넘겨 생성 시각을 분리한다.
    private static void awaitNextMillisecond() {
        long now = System.currentTimeMillis();
        while (System.currentTimeMillis() == now) {
            Thread.onSpinWait();
        }
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private UUID persistMember(String email) {
        Member member = Member.create(Email.of(email), "테스터", "password-hash", MemberRole.BUYER);
        entityManager.persist(member);
        return member.getId();
    }

    /** 회원의 주문을 저장한다. {@code paid}면 결제완료 상태로 전이해 둔다. */
    private UUID persistOrder(UUID memberId, boolean paid) {
        Order order = Order.place(
                memberId,
                List.of(new OrderLineSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), "티셔츠", "Red / L", Money.of(10000L), 1)),
                Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678"),
                Money.ZERO,
                Money.ZERO,
                null);
        if (paid) {
            order.markStockDeducted(Instant.now());
            order.markPaid(Instant.now());
        }
        entityManager.persist(order);
        return order.getId();
    }
}

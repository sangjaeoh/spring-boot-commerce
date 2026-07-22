package com.commerce.admin.web.v1;

import com.commerce.admin.SharedPostgresContainer;
import com.commerce.admin.SharedRedisContainer;
import com.commerce.auth.token.JwtTokenCodec;
import com.commerce.member.application.provided.MemberCredentialValidator;
import com.commerce.order.application.provided.OrderAppender;
import com.commerce.order.application.provided.OrderModifier;
import com.commerce.order.domain.Address;
import com.commerce.order.domain.OrderLineSnapshot;
import com.commerce.payment.application.provided.PaymentAppender;
import com.commerce.payment.application.provided.PaymentProcessor;
import com.commerce.payment.application.required.PaymentApproval;
import com.commerce.payment.application.required.PaymentGateway;
import com.commerce.payment.domain.PaymentMethod;
import com.commerce.product.application.info.ProductVariantInfo;
import com.commerce.product.application.provided.ProductReader;
import com.commerce.product.application.provided.ProductVariantReader;
import com.commerce.shared.entity.Money;
import com.commerce.stock.application.provided.StockModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 어드민 컨트롤러 웹 통합 테스트의 공통 하네스다.
 *
 * <p>{@link SharedPostgresContainer}의 공유 PostgreSQL 컨테이너에 붙어 앱을 MOCK 웹 환경으로 부팅해 실제
 * 필터 체인·핸들러가 붙은 {@link org.springframework.test.web.servlet.MockMvc}를 제공한다. 트랜잭션 롤백을
 * 걸지 않으므로(각 도메인 서비스가 자기 트랜잭션을 커밋) 테스트마다 임의 키로 데이터를 격리한다.
 * 관리자 시딩 속성을 주입하므로 컨텍스트 기동 시 {@link #ADMIN_EMAIL} 관리자 계정이 시딩된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class WebIntegrationTest {

    /** 기동 시딩되는 관리자 계정 자격증명. */
    protected static final String ADMIN_EMAIL = "admin@example.com";

    protected static final String ADMIN_PASSWORD = "admin-password-123!";

    private static @Nullable UUID seededAdminId;

    @Autowired
    private JwtTokenCodec jwtTokenCodec;

    @Autowired
    private MemberCredentialValidator memberCredentialValidator;

    @Autowired
    private ProductReader productReader;

    @Autowired
    private ProductVariantReader variantReader;

    @Autowired
    private OrderAppender orderAppender;

    @Autowired
    private OrderModifier orderModifier;

    @Autowired
    private StockModifier stockModifier;

    @Autowired
    private PaymentAppender paymentAppender;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private PaymentProcessor paymentProcessor;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.data.redis.host", SharedRedisContainer.INSTANCE::getRedisHost);
        registry.add("spring.data.redis.port", SharedRedisContainer.INSTANCE::getRedisPort);
        registry.add("auth.jwt.secret", () -> "test-secret-key-of-at-least-32-bytes!!");
        registry.add("auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("auth.admin.password", () -> ADMIN_PASSWORD);
    }

    /** 회원을 주체로 실은 구매자 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String bearer(UUID memberId) {
        return "Bearer " + jwtTokenCodec.issue(memberId.toString(), Map.of("role", "BUYER"));
    }

    /** 시딩된 관리자를 주체로 실은 관리자 {@code Authorization} 헤더 값(Bearer 토큰)을 만든다. */
    protected String adminBearer() {
        UUID adminId = seededAdminId;
        if (adminId == null) {
            adminId = memberCredentialValidator
                    .authenticate(ADMIN_EMAIL, ADMIN_PASSWORD)
                    .id();
            seededAdminId = adminId;
        }
        return "Bearer " + jwtTokenCodec.issue(adminId.toString(), Map.of("role", "ADMIN"));
    }

    /**
     * 회원의 변형 구매를 결제완료(PAID) 주문으로 재현한다 — app-api 체크아웃 경로(주문 생성·재고 차감·차감
     * 마커·결제 요청·PG 승인·승인 기록·결제완료 전이)를 도메인 서비스 직접 호출로 인라인한 픽스처다.
     */
    protected UUID placePaidOrder(UUID memberId, UUID variantId, int quantity) {
        ProductVariantInfo variant = variantReader.getVariant(variantId);
        String productName = productReader.getProduct(variant.productId()).name();
        OrderLineSnapshot snapshot = new OrderLineSnapshot(
                variant.id(), variant.productId(), productName, variant.optionLabel(), variant.price(), quantity);
        UUID orderId =
                orderAppender.place(memberId, List.of(snapshot), shippingAddress(), Money.ZERO, Money.ZERO, null);
        stockModifier.deduct(variantId, quantity);
        orderModifier.markStockDeducted(orderId);
        Money payAmount = variant.price().multiply(quantity);
        UUID paymentId = paymentAppender.request(orderId, payAmount, PaymentMethod.CARD);
        PaymentApproval approval = paymentGateway.approve(paymentId, payAmount, PaymentMethod.CARD);
        paymentProcessor.confirmApproval(paymentId, Objects.requireNonNull(approval.pgTransactionId()));
        orderModifier.markPaid(orderId);
        return orderId;
    }

    /** 픽스처 주문의 기본 배송지를 만든다. */
    protected static Address shippingAddress() {
        return Address.of("홍길동", "04524", "서울특별시 중구 세종대로 110", "3층", "010-1234-5678");
    }
}

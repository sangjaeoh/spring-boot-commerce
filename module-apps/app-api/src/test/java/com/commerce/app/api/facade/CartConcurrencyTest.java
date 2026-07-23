package com.commerce.app.api.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.domain.cart.application.info.CartInfo;
import com.commerce.domain.cart.application.info.CartItemInfo;
import com.commerce.domain.cart.application.provided.CartAppender;
import com.commerce.domain.cart.application.provided.CartReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestConstructor;

/**
 * 장바구니 쓰기 경로의 동시 담기 견고성을 검증하는 테스트다.
 *
 * <p>동시 최초 담기(장바구니·라인 중복 생성 유니크 경합)는 재조회-재시도로 전부 성공해 합산이 정확해야
 * 하고, 기존 라인 동시 합산은 낙관락이 유실을 막아 어떤 실패도 유니크 위반(500 계열)이 아니라 낙관락
 * 충돌(409 매핑)로만 표면화돼야 한다. 담기 자격 게이트는 상위 파사드 소유라 여기서는 쓰기 경로만 태운다.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CartConcurrencyTest extends FacadeIntegrationTest {

    private final CartAppender cartAppender;
    private final CartReader cartReader;

    CartConcurrencyTest(CartAppender cartAppender, CartReader cartReader) {
        this.cartAppender = cartAppender;
        this.cartReader = cartReader;
    }

    @Test
    @DisplayName("장바구니가 없는 회원의 동시 첫 담기 2건은 모두 성공하고 한 라인으로 합산된다")
    void concurrentFirstAddsConvergeToSingleMergedLine() {
        UUID memberId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        List<Throwable> failures = runConcurrently(2, () -> cartAppender.addItem(memberId, variantId, 1));

        assertThat(failures).isEmpty();
        CartInfo cart = cartReader.getCart(memberId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("장바구니가 없는 회원의 서로 다른 변형 동시 담기 2건은 모두 성공하고 두 라인이 된다")
    void concurrentFirstAddsOfDifferentVariantsBothLand() {
        UUID memberId = UUID.randomUUID();
        UUID firstVariantId = UUID.randomUUID();
        UUID secondVariantId = UUID.randomUUID();

        List<Throwable> failures = runConcurrently(List.of(
                () -> cartAppender.addItem(memberId, firstVariantId, 1),
                () -> cartAppender.addItem(memberId, secondVariantId, 3)));

        assertThat(failures).isEmpty();
        CartInfo cart = cartReader.getCart(memberId);
        assertThat(cart.items()).hasSize(2);
        assertThat(cart.items().stream().mapToInt(CartItemInfo::quantity).sum()).isEqualTo(4);
    }

    @Test
    @DisplayName("기존 라인 동시 합산은 유실 없이 성공 수만큼 정확히 더해지고 실패는 낙관락 충돌뿐이다")
    void concurrentMergesLoseNothingAndFailOnlyWithOptimisticConflict() {
        UUID memberId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        cartAppender.addItem(memberId, variantId, 1);

        List<Throwable> failures = runConcurrently(2, () -> cartAppender.addItem(memberId, variantId, 1));

        // 실패가 있다면 409로 매핑되는 낙관락 충돌뿐이다 — 유니크 위반(500)이 새어나가지 않는다.
        assertThat(failures)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(ObjectOptimisticLockingFailureException.class));
        int succeeded = 2 - failures.size();
        CartInfo cart = cartReader.getCart(memberId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(1 + succeeded);
    }

    private List<Throwable> runConcurrently(int threads, Runnable action) {
        List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    /** 모든 액션을 배리어로 정렬해 동시에 시작시키고, 발생한 실패를 모아 반환한다. */
    private List<Throwable> runConcurrently(List<Runnable> actions) {
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        try {
            for (Runnable action : actions) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.run();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                executor.shutdown();
                assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } finally {
            executor.shutdownNow();
        }
        return failures;
    }
}

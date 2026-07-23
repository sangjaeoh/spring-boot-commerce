package com.commerce.domain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.order.domain.exception.FulfillmentStatusException;
import com.commerce.domain.order.domain.exception.OrderErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FulfillmentTest {

    private static final Instant NOW = Instant.parse("2025-06-15T00:00:00Z");
    private static final String CARRIER = "CJ대한통운";
    private static final String TRACKING_NUMBER = "688900123456";
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("생성 시 PREPARING이고 준비→출고→배송 완료로 이행한다")
    void fulfillmentFlow() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(fulfillment.getOrderId()).isEqualTo(ORDER_ID);

        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.SHIPPED);

        fulfillment.confirmDelivery(NOW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("출고 시 택배사·운송장 번호를 기록하고 출고 전에는 null이다")
    void shipRecordsTrackingInfo() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        assertThat(fulfillment.getCarrier()).isNull();
        assertThat(fulfillment.getTrackingNumber()).isNull();

        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);

        assertThat(fulfillment.getCarrier()).isEqualTo(CARRIER);
        assertThat(fulfillment.getTrackingNumber()).isEqualTo(TRACKING_NUMBER);
    }

    @Test
    @DisplayName("준비 중이 아니면 출고할 수 없다")
    void shipRequiresPreparing() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION));
    }

    @Test
    @DisplayName("주문의 취소가 진행 중이면 출고를 거부한다")
    void shipRejectedWhileCancellationInProgress() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, true, NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.CANCEL_IN_PROGRESS));
    }

    @Test
    @DisplayName("출고된 상태가 아니면 배송 완료 처리할 수 없다")
    void confirmDeliveryRequiresShipped() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);

        assertThatThrownBy(() -> fulfillment.confirmDelivery(NOW))
                .isInstanceOfSatisfying(
                        FulfillmentStatusException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(OrderErrorCode.INVALID_FULFILLMENT_TRANSITION));
    }

    @Test
    @DisplayName("보류와 해제를 오간다. 보류 중에는 출고할 수 없다")
    void holdAndRelease() {
        Fulfillment fulfillment = Fulfillment.create(ORDER_ID);
        fulfillment.hold(HoldReason.FRAUD_REVIEW);
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.ON_HOLD);

        assertThatThrownBy(() -> fulfillment.ship(CARRIER, TRACKING_NUMBER, false, NOW))
                .isInstanceOf(FulfillmentStatusException.class);

        fulfillment.release();
        assertThat(fulfillment.getStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        assertThat(fulfillment.getHoldReason()).isNull();
    }

    @Test
    @DisplayName("준비 중이 아니면 보류할 수 없고, 보류 중이 아니면 해제할 수 없다")
    void holdAndReleaseRequireExpectedState() {
        Fulfillment shipped = Fulfillment.create(ORDER_ID);
        shipped.ship(CARRIER, TRACKING_NUMBER, false, NOW);
        assertThatThrownBy(() -> shipped.hold(HoldReason.STOCK_DELAY)).isInstanceOf(FulfillmentStatusException.class);

        Fulfillment preparing = Fulfillment.create(ORDER_ID);
        assertThatThrownBy(preparing::release).isInstanceOf(FulfillmentStatusException.class);
    }
}

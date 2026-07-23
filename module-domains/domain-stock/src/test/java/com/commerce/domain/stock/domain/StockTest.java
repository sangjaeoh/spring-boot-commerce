package com.commerce.domain.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.domain.stock.domain.exception.StockShortageException;
import com.commerce.domain.stock.domain.exception.StockStatusException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockTest {

    private Stock sellableStock(int quantity) {
        return Stock.create(UUID.randomUUID(), quantity);
    }

    @Test
    @DisplayName("생성 시 SELLABLE이고 초기 수량이 설정된다")
    void createsSellable() {
        Stock stock = sellableStock(10);
        assertThat(stock.getStatus()).isEqualTo(StockStatus.SELLABLE);
        assertThat(stock.getQuantity()).isEqualTo(10);
        assertThat(stock.getId()).isNotNull();
    }

    @Test
    @DisplayName("초기 수량이 음수면 생성할 수 없다")
    void rejectsNegativeInitialQuantity() {
        assertThatThrownBy(() -> Stock.create(UUID.randomUUID(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("차감하면 수량이 줄어든다")
    void deductReducesQuantity() {
        Stock stock = sellableStock(10);
        stock.deduct(3);
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("가용 수량보다 많이 차감하면 재고 부족")
    void deductBeyondQuantityThrows() {
        assertThatThrownBy(() -> sellableStock(2).deduct(3)).isInstanceOf(StockShortageException.class);
    }

    @Test
    @DisplayName("복원은 상태와 무관하게 수량을 늘린다")
    void restoreIgnoresStatus() {
        Stock stock = sellableStock(1);
        stock.discontinue();
        stock.restore(5);
        assertThat(stock.getQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("단종 재고는 재입고할 수 없다")
    void cannotIncreaseWhenDiscontinued() {
        Stock stock = sellableStock(0);
        stock.discontinue();
        assertThatThrownBy(() -> stock.increase(5)).isInstanceOf(StockStatusException.class);
    }

    @Test
    @DisplayName("수동 품절과 판매 재개를 오간다")
    void markSoldOutAndSellable() {
        Stock stock = sellableStock(10);
        stock.markSoldOut();
        assertThat(stock.getStatus()).isEqualTo(StockStatus.SOLD_OUT);
        stock.markSellable();
        assertThat(stock.getStatus()).isEqualTo(StockStatus.SELLABLE);
    }

    @Test
    @DisplayName("이미 품절인 재고는 다시 품절할 수 없다")
    void cannotMarkSoldOutTwice() {
        Stock stock = sellableStock(10);
        stock.markSoldOut();
        assertThatThrownBy(stock::markSoldOut).isInstanceOf(StockStatusException.class);
    }

    @Test
    @DisplayName("단종은 종료 상태라 다시 단종할 수 없다")
    void discontinueIsTerminal() {
        Stock stock = sellableStock(10);
        stock.discontinue();
        assertThat(stock.getStatus()).isEqualTo(StockStatus.DISCONTINUED);
        assertThatThrownBy(stock::discontinue).isInstanceOf(StockStatusException.class);
    }
}

package com.commerce.app.batch.config;

import com.commerce.common.event.registry.EventTypeRegistry;
import com.commerce.common.event.registry.MapEventTypeRegistry;
import com.commerce.event.order.OrderPaid;
import com.commerce.event.stock.StockRestocked;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 아웃박스 릴레이가 논리 타입 키를 해석할 {@link EventTypeRegistry}를 배선하는 설정이다. */
@Configuration
public class EventTypeRegistryConfig {

    /** 이 앱이 재발행하는 이벤트의 논리 타입 키 → 이벤트 클래스 매핑을 공급한다. */
    @Bean
    public EventTypeRegistry eventTypeRegistry() {
        return new MapEventTypeRegistry(
                Map.of(OrderPaid.EVENT_TYPE, OrderPaid.class, StockRestocked.EVENT_TYPE, StockRestocked.class));
    }
}

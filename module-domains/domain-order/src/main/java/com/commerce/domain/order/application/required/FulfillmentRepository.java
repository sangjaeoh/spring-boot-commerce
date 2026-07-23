package com.commerce.domain.order.application.required;

import com.commerce.domain.order.domain.Fulfillment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {

    Optional<Fulfillment> findByOrderId(UUID orderId);

    List<Fulfillment> findByOrderIdIn(Collection<UUID> orderIds);
}

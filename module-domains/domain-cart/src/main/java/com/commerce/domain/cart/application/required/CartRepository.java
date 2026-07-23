package com.commerce.domain.cart.application.required;

import com.commerce.domain.cart.domain.Cart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByMemberId(UUID memberId);
}

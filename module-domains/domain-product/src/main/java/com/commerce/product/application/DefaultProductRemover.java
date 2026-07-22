package com.commerce.product.application;

import com.commerce.product.application.provided.ProductRemover;
import com.commerce.product.application.required.ProductRepository;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductErrorCode;
import com.commerce.product.domain.ProductNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProductRemover}의 기본 구현이다. */
@Service
class DefaultProductRemover implements ProductRemover {

    private final ProductRepository productRepository;
    private final Clock clock;

    DefaultProductRemover(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void delete(UUID productId) {
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductNotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.delete(clock.instant());
    }
}

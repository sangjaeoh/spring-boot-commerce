package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.cart.info.CartInfo;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartReader;
import com.commerce.core.money.Money;
import com.commerce.coupon.info.DiscountPreviewInfo;
import com.commerce.coupon.service.IssuedCouponModifier;
import com.commerce.coupon.service.IssuedCouponReader;
import com.commerce.member.entity.MemberStatus;
import com.commerce.member.info.MemberInfo;
import com.commerce.member.service.MemberReader;
import com.commerce.order.entity.Address;
import com.commerce.order.entity.CancellationReason;
import com.commerce.order.entity.OrderLineSnapshot;
import com.commerce.order.service.OrderAppender;
import com.commerce.order.service.OrderModifier;
import com.commerce.payment.entity.PaymentMethod;
import com.commerce.payment.entity.PaymentStatus;
import com.commerce.payment.info.PaymentInfo;
import com.commerce.payment.service.PaymentAppender;
import com.commerce.payment.service.PaymentProcessor;
import com.commerce.product.entity.ProductStatus;
import com.commerce.product.entity.ProductVariantStatus;
import com.commerce.product.exception.ProductNotFoundException;
import com.commerce.product.exception.ProductVariantNotFoundException;
import com.commerce.product.info.ProductInfo;
import com.commerce.product.info.ProductVariantInfo;
import com.commerce.product.service.ProductReader;
import com.commerce.product.service.ProductVariantReader;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.exception.StockNotFoundException;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 장바구니 전체를 주문·결제로 전환하는 체크아웃 흐름을 조율한다.
 *
 * <p>트랜잭션을 열지 않고 도메인 서비스를 순차 호출한다(각 서비스가 자기 트랜잭션 소유). 주문 PENDING을
 * 사가 앵커로 먼저 만들고, 재고 차감·쿠폰 확정·결제 중 실패하면 그 콜스택에서 동기 보상한다. 전 라인 재고
 * 차감이 완료되면 주문에 차감 완료 마커를 남긴다 — 스윕·리컨실 보상은 이 증거 없이 재고를 복원하지 않으므로
 * 차감 전·차감 중 중단 잔여가 재고를 증식시키지 않는다. 보상은 스윕·
 * 리컨실과 같은 순서로 주문 취소의 1회성 전이를 복원 앞에 둔다 — 취소가 실패하면 복원하지 않아 주문이
 * PENDING으로 남고, 잔여는 payment 행 상태에 따라 PENDING 스윕(행 없음은 직접 보상, 종결 기록된
 * APPROVED·FAILED는 결제 리컨실에 위임)·결제 리컨실(REQUESTED)이 인계한다(DOMAIN_MODEL.md 체크아웃 정책
 * 참조). 결제 성공 후 {@code markPaid}가 {@code OrderPaid}를 발행해 커밋 후 장바구니가 비워진다.
 */
@Component
public class CheckoutFacade {

    private final MemberReader memberReader;
    private final CartReader cartReader;
    private final ProductVariantReader variantReader;
    private final ProductReader productReader;
    private final StockReader stockReader;
    private final IssuedCouponReader issuedCouponReader;
    private final OrderAppender orderAppender;
    private final StockModifier stockModifier;
    private final IssuedCouponModifier issuedCouponModifier;
    private final PaymentAppender paymentAppender;
    private final PaymentProcessor paymentProcessor;
    private final OrderModifier orderModifier;

    public CheckoutFacade(
            MemberReader memberReader,
            CartReader cartReader,
            ProductVariantReader variantReader,
            ProductReader productReader,
            StockReader stockReader,
            IssuedCouponReader issuedCouponReader,
            OrderAppender orderAppender,
            StockModifier stockModifier,
            IssuedCouponModifier issuedCouponModifier,
            PaymentAppender paymentAppender,
            PaymentProcessor paymentProcessor,
            OrderModifier orderModifier) {
        this.memberReader = memberReader;
        this.cartReader = cartReader;
        this.variantReader = variantReader;
        this.productReader = productReader;
        this.stockReader = stockReader;
        this.issuedCouponReader = issuedCouponReader;
        this.orderAppender = orderAppender;
        this.stockModifier = stockModifier;
        this.issuedCouponModifier = issuedCouponModifier;
        this.paymentAppender = paymentAppender;
        this.paymentProcessor = paymentProcessor;
        this.orderModifier = orderModifier;
    }

    /**
     * 체크아웃을 수행하고 결제 완료된 주문 ID를 반환한다.
     *
     * @throws ApiException 회원 자격·장바구니·주문 가능·쿠폰 적용성·결제 거절 등 크로스 도메인 정책 거부
     */
    public UUID checkout(
            UUID memberId,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        requireEligibleMember(memberId);
        List<OrderLineSnapshot> snapshots = buildOrderableSnapshots(requireNonEmptyCart(memberId));
        Money totalAmount = totalOf(snapshots);
        Money discountAmount =
                issuedCouponId != null ? resolveCouponDiscount(issuedCouponId, memberId, totalAmount) : Money.ZERO;
        Money payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
        PaymentMethod resolvedMethod = payAmount.isZero() ? null : requireMethod(method);

        UUID orderId =
                orderAppender.place(memberId, snapshots, shippingAddress, discountAmount, shippingFee, issuedCouponId);
        deductStockOrCompensate(orderId, snapshots);
        if (issuedCouponId != null) {
            useCouponOrCompensate(orderId, memberId, issuedCouponId, snapshots);
        }
        approvePaymentOrCompensate(orderId, snapshots, issuedCouponId, payAmount, resolvedMethod);
        orderModifier.markPaid(orderId);
        return orderId;
    }

    private void requireEligibleMember(UUID memberId) {
        MemberInfo member = memberReader.getMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_NOT_ELIGIBLE);
        }
    }

    private List<CartItemInfo> requireNonEmptyCart(UUID memberId) {
        CartInfo cart = cartReader.getCart(memberId);
        if (cart.items().isEmpty()) {
            throw new ApiException(ApiErrorCode.EMPTY_CART);
        }
        return cart.items();
    }

    private List<OrderLineSnapshot> buildOrderableSnapshots(List<CartItemInfo> items) {
        List<OrderLineSnapshot> snapshots = new ArrayList<>();
        for (CartItemInfo item : items) {
            ProductVariantInfo variant = requireActiveVariant(item.variantId());
            ProductInfo product = requireOnSaleProduct(variant.productId());
            requireSellableStock(item.variantId(), item.quantity());
            snapshots.add(new OrderLineSnapshot(
                    variant.id(),
                    product.id(),
                    product.name(),
                    variant.optionLabel(),
                    variant.price(),
                    item.quantity()));
        }
        return snapshots;
    }

    private Money totalOf(List<OrderLineSnapshot> snapshots) {
        Money total = Money.ZERO;
        for (OrderLineSnapshot line : snapshots) {
            total = total.plus(line.unitPrice().multiply(line.quantity()));
        }
        return total;
    }

    private ProductVariantInfo requireActiveVariant(UUID variantId) {
        ProductVariantInfo variant;
        try {
            variant = variantReader.getVariant(variantId);
        } catch (ProductVariantNotFoundException e) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (variant.status() != ProductVariantStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        return variant;
    }

    private ProductInfo requireOnSaleProduct(UUID productId) {
        ProductInfo product;
        try {
            product = productReader.getProduct(productId);
        } catch (ProductNotFoundException e) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (product.status() != ProductStatus.ON_SALE) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        return product;
    }

    private void requireSellableStock(UUID variantId, int quantity) {
        StockInfo stock;
        try {
            stock = stockReader.getByVariantId(variantId);
        } catch (StockNotFoundException e) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (stock.status() != StockStatus.SELLABLE) {
            throw new ApiException(ApiErrorCode.NOT_ORDERABLE);
        }
        if (stock.quantity() < quantity) {
            throw new ApiException(ApiErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private Money resolveCouponDiscount(UUID issuedCouponId, UUID memberId, Money totalAmount) {
        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedCouponId, memberId, totalAmount);
        if (!preview.applicable()) {
            throw new ApiException(ApiErrorCode.COUPON_NOT_APPLICABLE);
        }
        return preview.discountAmount();
    }

    private PaymentMethod requireMethod(@Nullable PaymentMethod method) {
        if (method == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_METHOD_REQUIRED);
        }
        return method;
    }

    private void deductStockOrCompensate(UUID orderId, List<OrderLineSnapshot> snapshots) {
        List<OrderLineSnapshot> deducted = new ArrayList<>();
        try {
            for (OrderLineSnapshot line : snapshots) {
                stockModifier.deduct(line.variantId(), line.quantity());
                deducted.add(line);
            }
            // 차감 완료 증거는 전 라인 차감 뒤에만 기록한다 — 마커가 있으면 차감이 완료됐음이 보장돼,
            // 스윕·리컨실 보상이 이 증거 없이는 재고를 복원하지 않는다(차감 안 된 라인의 과복원 차단).
            // 마커 기록 실패는 아래 catch가 동기 보상한다(이 시점엔 전 라인이 차감돼 전량 복원이 정확하다).
            orderModifier.markStockDeducted(orderId);
        } catch (RuntimeException e) {
            orderModifier.cancel(orderId, CancellationReason.STOCK_SHORTAGE);
            restoreStock(deducted);
            throw e;
        }
    }

    private void useCouponOrCompensate(
            UUID orderId, UUID memberId, UUID issuedCouponId, List<OrderLineSnapshot> snapshots) {
        try {
            issuedCouponModifier.use(issuedCouponId, memberId, orderId);
        } catch (RuntimeException e) {
            orderModifier.cancel(orderId, CancellationReason.COUPON_CONFLICT);
            restoreStock(snapshots);
            throw e;
        }
    }

    private void approvePaymentOrCompensate(
            UUID orderId,
            List<OrderLineSnapshot> snapshots,
            @Nullable UUID issuedCouponId,
            Money payAmount,
            @Nullable PaymentMethod method) {
        PaymentInfo payment;
        try {
            UUID paymentId = paymentAppender.request(orderId, payAmount, method);
            payment = paymentProcessor.approve(paymentId);
        } catch (RuntimeException e) {
            compensate(orderId, snapshots, issuedCouponId);
            throw e;
        }
        if (payment.status() != PaymentStatus.APPROVED) {
            compensate(orderId, snapshots, issuedCouponId);
            throw new ApiException(ApiErrorCode.PAYMENT_DECLINED);
        }
    }

    private void compensate(UUID orderId, List<OrderLineSnapshot> snapshots, @Nullable UUID issuedCouponId) {
        orderModifier.cancel(orderId, CancellationReason.PAYMENT_FAILED);
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        restoreStock(snapshots);
    }

    private void restoreStock(List<OrderLineSnapshot> lines) {
        for (OrderLineSnapshot line : lines) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }
}

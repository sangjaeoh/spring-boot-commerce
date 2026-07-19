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
 * <p>트랜잭션을 열지 않고 도메인 서비스를 순차 호출한다(각 서비스가 자기 트랜잭션 소유).
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
     * 체크아웃을 수행하고 결제 완료된 주문 ID를 반환한다. 4단계 이후 실패는 그 콜스택에서 동기 보상한다.
     *
     * @throws ApiException 회원 자격·장바구니·주문 가능·쿠폰 적용성·결제 거절 등 크로스 도메인 정책 거부
     */
    public UUID checkout(
            UUID memberId,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        // 1. 회원 자격 확인
        requireEligibleMember(memberId);
        // 2. 장바구니를 주문 라인 스냅샷으로 변환
        List<OrderLineSnapshot> snapshots = buildOrderableSnapshots(requireNonEmptyCart(memberId));
        // 3. 할인·실청구액 산출
        Money totalAmount = totalOf(snapshots);
        Money discountAmount =
                issuedCouponId != null ? resolveCouponDiscount(issuedCouponId, memberId, totalAmount) : Money.ZERO;
        Money payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
        PaymentMethod resolvedMethod = payAmount.isZero() ? null : requireMethod(method);

        // 4. 주문 생성 — 이후 단계 실패 시 보상 앵커
        UUID orderId =
                orderAppender.place(memberId, snapshots, shippingAddress, discountAmount, shippingFee, issuedCouponId);
        // 5. 재고 차감
        deductStockOrCompensate(orderId, snapshots);
        // 6. 쿠폰 사용 확정
        if (issuedCouponId != null) {
            useCouponOrCompensate(orderId, memberId, issuedCouponId, snapshots);
        }
        // 7. 결제 승인
        approvePaymentOrCompensate(orderId, snapshots, issuedCouponId, payAmount, resolvedMethod);
        // 8. 결제 완료 확정
        orderModifier.markPaid(orderId);
        return orderId;
    }

    /** 회원이 활성 자격인지 확인한다. */
    private void requireEligibleMember(UUID memberId) {
        MemberInfo member = memberReader.getMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_NOT_ELIGIBLE);
        }
    }

    /** 장바구니가 비어 있지 않은지 확인하고 라인을 반환한다. */
    private List<CartItemInfo> requireNonEmptyCart(UUID memberId) {
        CartInfo cart = cartReader.getCart(memberId);
        if (cart.items().isEmpty()) {
            throw new ApiException(ApiErrorCode.EMPTY_CART);
        }
        return cart.items();
    }

    /** 장바구니 라인마다 주문 가능 게이트를 통과시키고 주문 라인 스냅샷으로 옮긴다. */
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

    /** 스냅샷 단가와 수량을 곱해 합산한 라인 합계를 계산한다. */
    private Money totalOf(List<OrderLineSnapshot> snapshots) {
        Money total = Money.ZERO;
        for (OrderLineSnapshot line : snapshots) {
            total = total.plus(line.unitPrice().multiply(line.quantity()));
        }
        return total;
    }

    /** 변형이 활성인지 확인한다. 미존재도 주문 불가로 합친다. */
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

    /** 상품이 판매중인지 확인한다. 미존재도 주문 불가로 합친다. */
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

    /** 재고가 판매 가능하고 요청 수량을 채우는지 확인한다. */
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

    /** 쿠폰 적용성을 확인하고 할인액을 산출한다. */
    private Money resolveCouponDiscount(UUID issuedCouponId, UUID memberId, Money totalAmount) {
        DiscountPreviewInfo preview = issuedCouponReader.getDiscountPreview(issuedCouponId, memberId, totalAmount);
        if (!preview.applicable()) {
            throw new ApiException(ApiErrorCode.COUPON_NOT_APPLICABLE);
        }
        return preview.discountAmount();
    }

    /** 유료 주문에 결제 수단이 있는지 확인한다. */
    private PaymentMethod requireMethod(@Nullable PaymentMethod method) {
        if (method == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_METHOD_REQUIRED);
        }
        return method;
    }

    /** 전 라인 재고를 차감하고 차감 완료를 기록한다. 실패하면 주문을 취소하고 차감분을 복원한다. */
    private void deductStockOrCompensate(UUID orderId, List<OrderLineSnapshot> snapshots) {
        List<OrderLineSnapshot> deducted = new ArrayList<>();
        try {
            for (OrderLineSnapshot line : snapshots) {
                stockModifier.deduct(line.variantId(), line.quantity());
                deducted.add(line);
            }
            // 마커가 곧 전 라인 차감 완료 증거라 루프 뒤에만 기록한다.
            orderModifier.markStockDeducted(orderId);
        } catch (RuntimeException e) {
            orderModifier.cancel(orderId, CancellationReason.STOCK_SHORTAGE);
            restoreStock(deducted);
            throw e;
        }
    }

    /** 쿠폰 사용을 확정한다. 실패하면 주문을 취소하고 재고를 복원한다. */
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

    /** 결제를 요청·승인한다. 실패하거나 승인되지 않으면 보상한다. */
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

    /** 주문을 취소한 뒤 쿠폰·재고를 복원한다. */
    private void compensate(UUID orderId, List<OrderLineSnapshot> snapshots, @Nullable UUID issuedCouponId) {
        orderModifier.cancel(orderId, CancellationReason.PAYMENT_FAILED);
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        restoreStock(snapshots);
    }

    /** 라인별 재고를 되돌린다. */
    private void restoreStock(List<OrderLineSnapshot> lines) {
        for (OrderLineSnapshot line : lines) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }
}

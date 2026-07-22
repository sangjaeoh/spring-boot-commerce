package com.commerce.api.facade;

import com.commerce.api.exception.ApiErrorCode;
import com.commerce.api.exception.ApiException;
import com.commerce.api.facade.view.CheckoutPreviewView;
import com.commerce.cart.info.CartInfo;
import com.commerce.cart.info.CartItemInfo;
import com.commerce.cart.service.CartReader;
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
import com.commerce.shared.entity.Money;
import com.commerce.stock.entity.StockStatus;
import com.commerce.stock.exception.StockNotFoundException;
import com.commerce.stock.info.StockInfo;
import com.commerce.stock.service.StockModifier;
import com.commerce.stock.service.StockReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** 장바구니 전체(체크아웃) 또는 요청 라인(바로구매)을 주문·결제로 전환하는 흐름과 금액 미리보기를 조율하는 파사드다. */
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
     * 체크아웃을 수행하고 결제 완료된 주문 ID를 반환한다. 실패하면 그 콜스택에서 동기 보상한다.
     *
     * @throws ApiException 회원 자격·장바구니·주문 가능·쿠폰 적용성·결제 거절 등 크로스 도메인 정책 거부
     */
    public UUID checkout(
            UUID memberId,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        return checkout(memberId, null, shippingAddress, shippingFee, issuedCouponId, method);
    }

    /**
     * 선택한 장바구니 라인만 체크아웃하고 결제 완료된 주문 ID를 반환한다. {@code variantIds}가 null이면 장바구니
     * 전체를 체크아웃한다. 실패하면 그 콜스택에서 동기 보상한다.
     *
     * @throws ApiException 회원 자격·장바구니·선택 라인 부재·주문 가능·쿠폰 적용성·결제 거절 등 크로스 도메인 정책 거부
     */
    public UUID checkout(
            UUID memberId,
            @Nullable List<UUID> variantIds,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        // 1. 회원 자격 확인
        requireEligibleMember(memberId);
        // 2. 장바구니 선택 라인을 주문 라인 스냅샷으로 변환
        List<OrderLineSnapshot> snapshots =
                buildOrderableSnapshots(selectItems(requireNonEmptyCart(memberId), variantIds));
        // 3. 주문·결제 실행
        return placeAndPay(memberId, snapshots, shippingAddress, shippingFee, issuedCouponId, method);
    }

    /**
     * 장바구니를 거치지 않고 요청 라인으로 주문·결제를 수행하고 결제 완료된 주문 ID를 반환한다. 장바구니는 변경하지
     * 않으며, 실패하면 그 콜스택에서 동기 보상한다.
     *
     * @throws ApiException 회원 자격·주문 가능·쿠폰 적용성·결제 거절 등 크로스 도메인 정책 거부
     */
    public UUID orderDirect(
            UUID memberId,
            List<DirectOrderLine> lines,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        // 1. 회원 자격 확인
        requireEligibleMember(memberId);
        // 2. 요청 라인을 주문 라인 스냅샷으로 변환
        List<OrderLineSnapshot> snapshots = new ArrayList<>();
        for (DirectOrderLine line : lines) {
            snapshots.add(orderableSnapshotOf(line.variantId(), line.quantity()));
        }
        // 3. 주문·결제 실행
        return placeAndPay(memberId, snapshots, shippingAddress, shippingFee, issuedCouponId, method);
    }

    /** 금액을 산출하고 주문 생성→재고 차감→쿠폰 사용→결제 승인→완료 확정을 실행한다. 실패하면 그 콜스택에서 동기 보상한다. */
    private UUID placeAndPay(
            UUID memberId,
            List<OrderLineSnapshot> snapshots,
            Address shippingAddress,
            Money shippingFee,
            @Nullable UUID issuedCouponId,
            @Nullable PaymentMethod method) {
        // 1. 할인·실청구액 산출
        Money totalAmount = totalOf(snapshots);
        Money discountAmount =
                issuedCouponId != null ? resolveCouponDiscount(issuedCouponId, memberId, totalAmount) : Money.ZERO;
        Money payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
        PaymentMethod resolvedMethod = payAmount.isZero() ? null : requireMethod(method);

        // 2. 주문 생성 — 이후 단계 실패 시 보상 앵커
        UUID orderId =
                orderAppender.place(memberId, snapshots, shippingAddress, discountAmount, shippingFee, issuedCouponId);
        // 3. 재고 차감
        deductStockOrCompensate(orderId, snapshots);
        // 4. 쿠폰 사용 확정
        if (issuedCouponId != null) {
            useCouponOrCompensate(orderId, memberId, issuedCouponId, snapshots);
        }
        // 5. 결제 승인
        approvePaymentOrCompensate(orderId, snapshots, issuedCouponId, payAmount, resolvedMethod);
        // 6. 결제 완료 확정
        orderModifier.markPaid(orderId);
        return orderId;
    }

    /**
     * 체크아웃과 같은 게이트·산식으로 결제 예정 금액을 계산해 반환한다. 주문·재고·쿠폰·결제에 부작용을 남기지 않는다.
     *
     * @throws ApiException 회원 자격·장바구니·주문 가능·쿠폰 적용성 등 크로스 도메인 정책 거부
     */
    public CheckoutPreviewView preview(
            UUID memberId, @Nullable List<UUID> variantIds, Money shippingFee, @Nullable UUID issuedCouponId) {
        // 1. 회원 자격 확인
        requireEligibleMember(memberId);
        // 2. 장바구니 선택 라인을 주문 라인 스냅샷으로 변환
        List<OrderLineSnapshot> snapshots =
                buildOrderableSnapshots(selectItems(requireNonEmptyCart(memberId), variantIds));
        // 3. 할인·실청구액 산출
        Money totalAmount = totalOf(snapshots);
        Money discountAmount =
                issuedCouponId != null ? resolveCouponDiscount(issuedCouponId, memberId, totalAmount) : Money.ZERO;
        Money payAmount = totalAmount.minus(discountAmount).plus(shippingFee);
        return new CheckoutPreviewView(totalAmount, discountAmount, shippingFee, payAmount);
    }

    /** 회원이 활성 자격인지 확인한다. */
    private void requireEligibleMember(UUID memberId) {
        MemberInfo member = memberReader.getMember(memberId);
        if (member.status() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.MEMBER_NOT_ELIGIBLE);
        }
    }

    /** 선택 목록이 있으면 장바구니 라인을 선택분으로 좁힌다. 선택한 변형이 장바구니에 없으면 거부한다. */
    private static List<CartItemInfo> selectItems(List<CartItemInfo> items, @Nullable List<UUID> variantIds) {
        if (variantIds == null) {
            return items;
        }
        Set<UUID> selected = Set.copyOf(variantIds);
        List<CartItemInfo> filtered = items.stream()
                .filter(item -> selected.contains(item.variantId()))
                .toList();
        // 장바구니 라인은 변형별 유일이라 매칭 수가 선택 수보다 적으면 장바구니에 없는 선택이 있다.
        if (filtered.size() < selected.size()) {
            throw new ApiException(ApiErrorCode.CART_ITEM_NOT_FOUND);
        }
        return filtered;
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
            snapshots.add(orderableSnapshotOf(item.variantId(), item.quantity()));
        }
        return snapshots;
    }

    /** 라인 하나를 주문 가능 게이트에 통과시켜 주문 라인 스냅샷으로 만든다. */
    private OrderLineSnapshot orderableSnapshotOf(UUID variantId, int quantity) {
        // 1. 변형 활성 확인
        ProductVariantInfo variant = requireActiveVariant(variantId);
        // 2. 변형이 속한 상품 판매중 확인
        ProductInfo product = requireOnSaleProduct(variant.productId());
        // 3. 재고 판매 가능·수량 확인
        requireSellableStock(variantId, quantity);
        // 4. 주문 라인 스냅샷 생성
        return new OrderLineSnapshot(
                variant.id(), product.id(), product.name(), variant.optionLabel(), variant.price(), quantity);
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
            // 1. 라인별 재고 차감
            for (OrderLineSnapshot line : snapshots) {
                stockModifier.deduct(line.variantId(), line.quantity());
                deducted.add(line);
            }
            // 2. 차감 완료 기록
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
        // 1. 결제 요청·PG 승인
        try {
            UUID paymentId = paymentAppender.request(orderId, payAmount, method);
            payment = paymentProcessor.approve(paymentId);
        } catch (RuntimeException e) {
            compensate(orderId, snapshots, issuedCouponId);
            throw e;
        }
        // 2. 거절 판정
        // PG가 정상 응답한 거절은 예외가 아니라 FAILED 상태로 돌아온다.
        if (payment.status() != PaymentStatus.APPROVED) {
            compensate(orderId, snapshots, issuedCouponId);
            throw new ApiException(ApiErrorCode.PAYMENT_DECLINED);
        }
    }

    /** 주문을 취소한 뒤 쿠폰·재고를 복원한다. */
    private void compensate(UUID orderId, List<OrderLineSnapshot> snapshots, @Nullable UUID issuedCouponId) {
        // 1. 주문 취소
        orderModifier.cancel(orderId, CancellationReason.PAYMENT_FAILED);
        // 2. 쿠폰 복원
        if (issuedCouponId != null) {
            issuedCouponModifier.restoreUse(issuedCouponId, orderId);
        }
        // 3. 재고 복원
        restoreStock(snapshots);
    }

    /** 라인별 재고를 되돌린다. */
    private void restoreStock(List<OrderLineSnapshot> lines) {
        for (OrderLineSnapshot line : lines) {
            stockModifier.restore(line.variantId(), line.quantity());
        }
    }
}
